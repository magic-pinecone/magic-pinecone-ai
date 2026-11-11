"""LoRA fine-tuning of a small student on the teacher data (text only).

NOT RUN BY ITS AUTHOR: it was written on a machine without a GPU. Only
`--check-data` (no ML libraries needed) has been executed. The library calls
follow the documentation listed in ml/README.md as read on 2026-11-10; expect
to fix details on the first real run.

    pip install -r ml/requirements-train.txt          # in a separate venv / Colab
    python ml/train_lora.py --check-data --data ml/data/train.jsonl
    python ml/train_lora.py --data ml/data/train.jsonl \\
        --model aisingapore/Gemma-SEA-LION-v4.5-E2B-IT --output ml/runs/sealion-e2b

Students (`--model`):
    aisingapore/Gemma-SEA-LION-v4.5-E2B-IT   main (Gemma 4 E2B architecture)
    google/gemma-4-E2B-it                    control
    google/gemma-3-1b-it                     1B control (gated: needs HF_TOKEN)

Backends (`--backend`):
    unsloth  FastModel + SFTTrainer + train_on_responses_only (default)
    peft     transformers + PEFT + TRL, prompt/completion dataset

In both, the loss is computed on the assistant turn only. The data is the chat
JSONL written by make_teacher_data.py (`messages`: system, user, assistant);
its prompt format is defined once in prompt_format.py.

Defaults are sized for an 8 GB RTX 4070: 4-bit loading, batch size 1 with
gradient accumulation 8, sequence length 2048.

After training: the adapter is in <output>/adapter. `--merge` also writes
merged 16-bit weights to <output>/merged, `--gguf q4_k_m` (unsloth backend)
exports a GGUF. See ml/README.md for the manual llama.cpp route.
"""

import argparse
import json
import random
from pathlib import Path

MODELS = {  # name: family
    "aisingapore/Gemma-SEA-LION-v4.5-E2B-IT": "gemma4",
    "google/gemma-4-E2B-it": "gemma4",
    "google/gemma-3-1b-it": "gemma3",
}
FAMILIES = {  # Unsloth chat template name and the turn markers of the template
    "gemma4": {
        "chat_template": "gemma-4",  # the non-thinking template
        "instruction_part": "<|turn>user\n",
        "response_part": "<|turn>model\n",
        # The Gemma 4 processor does not add <bos> when it tokenises the training text, so the one
        # from the chat template has to stay (checked on Colab, 2026-11-10: exactly one <bos>).
        "strip_bos": False,
    },
    "gemma3": {
        "chat_template": "gemma-3",
        "instruction_part": "<start_of_turn>user\n",
        "response_part": "<start_of_turn>model\n",
        # The Gemma 3 tokenizer adds <bos> itself, according to the Unsloth Gemma 3 notebook. Not
        # checked here: decode one training example before a real run and expect exactly one <bos>.
        "strip_bos": True,
    },
}


def training_text(rendered: str, strip_bos: bool) -> str:
    """The chat-template output as it goes into training, with exactly one <bos> after tokenising."""
    return rendered.removeprefix("<bos>") if strip_bos else rendered


LORA_TARGETS = [
    "q_proj",
    "k_proj",
    "v_proj",
    "o_proj",
    "gate_proj",
    "up_proj",
    "down_proj",
]


def load_rows(path: Path) -> list[dict]:
    """Rows of the training file, checked for the system/user/assistant shape."""
    rows = []
    with path.open(encoding="utf-8") as handle:
        for number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            row = json.loads(line)
            roles = [message["role"] for message in row["messages"]]
            if roles != ["system", "user", "assistant"]:
                raise ValueError(f"{path}:{number}: unexpected roles {roles}")
            if not all(m["content"].strip() for m in row["messages"]):
                raise ValueError(f"{path}:{number}: empty message")
            rows.append(row)
    if not rows:
        raise ValueError(f"{path} has no examples")
    return rows


def split_rows(rows: list[dict], eval_fraction: float, seed: int):
    rows = rows[:]
    random.Random(seed).shuffle(rows)
    held_out = int(len(rows) * eval_fraction)
    return rows[held_out:], rows[:held_out]


def describe(rows: list[dict]) -> str:
    def count(field: str) -> dict:
        counts: dict = {}
        for row in rows:
            counts[row.get(field, "?")] = counts.get(row.get(field, "?"), 0) + 1
        return dict(sorted(counts.items()))

    longest = max(sum(len(m["content"]) for m in row["messages"]) for row in rows)
    return (
        f"{len(rows)} examples; by lang {count('lang')}; by kind {count('kind')}; "
        f"by teacher {count('teacher')}; longest example {longest} characters"
    )


def train_unsloth(args, family: dict, train_rows: list[dict], eval_rows: list[dict]):
    # Unsloth must be imported before transformers.
    from unsloth import FastModel  # isort: skip
    from datasets import Dataset
    from trl import SFTConfig, SFTTrainer
    from unsloth.chat_templates import get_chat_template, train_on_responses_only

    model, tokenizer = FastModel.from_pretrained(
        model_name=args.model,
        max_seq_length=args.max_seq_length,
        dtype=None,
        load_in_4bit=args.load_in_4bit,
        full_finetuning=False,
    )
    model = FastModel.get_peft_model(
        model,
        finetune_vision_layers=False,  # text only
        finetune_language_layers=True,
        finetune_attention_modules=True,
        finetune_mlp_modules=True,
        r=args.rank,
        lora_alpha=args.alpha,
        lora_dropout=args.dropout,
        bias="none",
        random_state=args.seed,
    )
    tokenizer = get_chat_template(tokenizer, chat_template=family["chat_template"])

    def to_text(rows: list[dict]):
        texts = [
            training_text(
                tokenizer.apply_chat_template(
                    row["messages"], tokenize=False, add_generation_prompt=False
                ),
                family["strip_bos"],
            )
            for row in rows
        ]
        return Dataset.from_dict({"text": texts})

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=to_text(train_rows),
        eval_dataset=to_text(eval_rows) if eval_rows else None,
        args=SFTConfig(
            dataset_text_field="text",
            output_dir=str(args.output / "checkpoints"),
            per_device_train_batch_size=args.batch_size,
            gradient_accumulation_steps=args.grad_accum,
            num_train_epochs=args.epochs,
            max_steps=args.max_steps,
            warmup_steps=args.warmup_steps,
            learning_rate=args.learning_rate,
            lr_scheduler_type="linear",
            weight_decay=0.001,
            optim="adamw_8bit",
            logging_steps=10,
            save_strategy="epoch",
            seed=args.seed,
            report_to="none",
        ),
    )
    # Mask everything before each model turn so only the answers are learned.
    trainer = train_on_responses_only(
        trainer,
        instruction_part=family["instruction_part"],
        response_part=family["response_part"],
    )
    trainer.train()
    if eval_rows:
        print("eval:", trainer.evaluate())

    adapter = args.output / "adapter"
    model.save_pretrained(str(adapter))
    tokenizer.save_pretrained(str(adapter))
    print(f"adapter saved to {adapter}")
    if args.merge:
        merged = args.output / "merged"
        model.save_pretrained_merged(str(merged), tokenizer, save_method="merged_16bit")
        print(f"merged 16-bit model saved to {merged}")
    if args.gguf:
        gguf = args.output / "gguf"
        model.save_pretrained_gguf(str(gguf), tokenizer, quantization_method=args.gguf)
        print(f"GGUF ({args.gguf}) saved to {gguf}")


def train_peft(args, family_name: str, train_rows: list[dict], eval_rows: list[dict]):
    import torch
    import transformers
    from datasets import Dataset
    from peft import LoraConfig, PeftModel
    from transformers import AutoTokenizer, BitsAndBytesConfig
    from trl import SFTConfig, SFTTrainer

    # Gemma 4 checkpoints are loaded with AutoModelForMultimodalLM (Google's
    # QLoRA guide); gemma-3-1b-it is a text-only AutoModelForCausalLM.
    model_class = getattr(
        transformers,
        "AutoModelForMultimodalLM"
        if family_name == "gemma4"
        else "AutoModelForCausalLM",
    )
    bf16 = torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    dtype = torch.bfloat16 if bf16 else torch.float16
    quantization = (
        BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_use_double_quant=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=dtype,
        )
        if args.load_in_4bit
        else None
    )
    model = model_class.from_pretrained(
        args.model, dtype=dtype, device_map="auto", quantization_config=quantization
    )
    tokenizer = AutoTokenizer.from_pretrained(args.model)

    # Conversational prompt/completion rows: TRL applies the model's own chat
    # template and computes the loss on the completion (assistant turn) only.
    def to_dataset(rows: list[dict]):
        return Dataset.from_list(
            [
                {"prompt": row["messages"][:2], "completion": row["messages"][2:]}
                for row in rows
            ]
        )

    lora = LoraConfig(
        r=args.rank,
        lora_alpha=args.alpha,
        lora_dropout=args.dropout,
        bias="none",
        task_type="CAUSAL_LM",
        # For Gemma 4, PEFT's default targets cover the language model layers
        # only; the text-only Gemma 3 gets the usual projection modules.
        target_modules=None if family_name == "gemma4" else LORA_TARGETS,
    )
    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        peft_config=lora,
        train_dataset=to_dataset(train_rows),
        eval_dataset=to_dataset(eval_rows) if eval_rows else None,
        args=SFTConfig(
            output_dir=str(args.output / "checkpoints"),
            max_length=args.max_seq_length,
            completion_only_loss=True,
            per_device_train_batch_size=args.batch_size,
            per_device_eval_batch_size=1,
            gradient_accumulation_steps=args.grad_accum,
            gradient_checkpointing=True,
            num_train_epochs=args.epochs,
            max_steps=args.max_steps,
            warmup_steps=args.warmup_steps,
            learning_rate=args.learning_rate,
            lr_scheduler_type="linear",
            optim="adamw_torch_fused",
            bf16=bf16,
            fp16=not bf16,
            logging_steps=10,
            save_strategy="epoch",
            eval_strategy="epoch" if eval_rows else "no",
            seed=args.seed,
            report_to="none",
        ),
    )
    trainer.train()

    adapter = args.output / "adapter"
    trainer.model.save_pretrained(str(adapter))
    tokenizer.save_pretrained(str(adapter))
    print(f"adapter saved to {adapter}")
    if args.merge:
        # Reload unquantized: an adapter cannot be merged into 4-bit weights
        # for export. This needs CPU RAM for the whole model in 16 bit.
        del trainer, model
        torch.cuda.empty_cache()
        base = model_class.from_pretrained(
            args.model, dtype=dtype, low_cpu_mem_usage=True
        )
        merged_model = PeftModel.from_pretrained(base, str(adapter)).merge_and_unload()
        merged = args.output / "merged"
        merged_model.save_pretrained(
            str(merged), safe_serialization=True, max_shard_size="2GB"
        )
        tokenizer.save_pretrained(str(merged))
        print(f"merged model saved to {merged}")
    if args.gguf:
        print("--gguf is only automated in the unsloth backend; see ml/README.md.")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__.split("\n")[0],
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--data", type=Path, required=True, help="chat JSONL")
    parser.add_argument("--model", choices=sorted(MODELS), default=next(iter(MODELS)))
    parser.add_argument("--backend", choices=("unsloth", "peft"), default="unsloth")
    parser.add_argument("--output", type=Path, default=Path("ml/runs/student"))
    parser.add_argument("--max-seq-length", type=int, default=2048)
    parser.add_argument(
        "--load-in-4bit",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="QLoRA; turn off only with more than 8 GB of VRAM",
    )
    parser.add_argument("--rank", type=int, default=16)
    parser.add_argument("--alpha", type=int, default=16)
    parser.add_argument("--dropout", type=float, default=0.0)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--max-steps", type=int, default=-1, help="overrides --epochs")
    parser.add_argument("--warmup-steps", type=int, default=10)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--eval-fraction", type=float, default=0.05)
    parser.add_argument("--seed", type=int, default=3407)
    parser.add_argument("--merge", action="store_true", help="also save merged weights")
    parser.add_argument("--gguf", metavar="QUANT", help="e.g. q4_k_m (unsloth backend)")
    parser.add_argument(
        "--check-data",
        action="store_true",
        help="validate and describe the data, then exit (needs no ML libraries)",
    )
    args = parser.parse_args()

    rows = load_rows(args.data)
    train_rows, eval_rows = split_rows(rows, args.eval_fraction, args.seed)
    print(describe(rows))
    print(f"train {len(train_rows)}, held out {len(eval_rows)}; model {args.model}")
    if args.check_data:
        return

    family_name = MODELS[args.model]
    args.output.mkdir(parents=True, exist_ok=True)
    if args.backend == "unsloth":
        train_unsloth(args, FAMILIES[family_name], train_rows, eval_rows)
    else:
        train_peft(args, family_name, train_rows, eval_rows)


if __name__ == "__main__":
    main()
