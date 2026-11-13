# ml/ — evaluation and fine-tuning kit for the on-device student

Goal: a small on-device model that turns "question + retrieved OIA passages"
into a short bulleted answer with source numbers, or refuses and points to the
OIA when the passages do not hold the answer. A large open-weight teacher
(gpt-oss behind an OpenAI-compatible endpoint) writes the training data;
students are LoRA fine-tuned on it. Languages: English, Traditional Chinese
(zh-TW), Vietnamese (vi), Indonesian (id).

Nothing here changes `backend/` or `app/`. The scripts import the backend's
passage search (`backend/app/services/student_services.py`) and therefore run
with the backend's environment. No model of PRC origin is used anywhere in this
kit (teacher, students, and there are no embeddings); keep it that way.

## Files

| File | What it does |
|---|---|
| `prompt_format.py` | The one definition of the prompt: system prompt, passage numbering, refusal sentence per language, citation and refusal detection. Used by data generation, training data and evaluation. The text lives in `backend/app/services/answer_text.py`, shared with the assistant endpoint, and is loaded from there by file path (no backend dependencies needed). |
| `checks.py` | Pure text checks: strip a reasoning channel, numbers not supported by the passages (both from `answer_text.py` too), `must_contain` matching, similarity to golden questions. |
| `demo/` | A one-page demo of the backend's assistant chain; see "Demo page" below. |
| `common.py` | Paths, import of the backend service, JSONL helpers, the OpenAI-compatible chat client (`httpx`). |
| `golden/questions.jsonl` | 60 hand-written test questions (24 en, 12 each zh-TW / vi / id; 9 unanswerable). |
| `eval_retrieval.py` | hit@1 / hit@3 / ans@3 of the backend's keyword search on the golden set. |
| `make_teacher_data.py` | Asks the teacher(s) for training examples and cleans them. |
| `train_lora.py` | LoRA fine-tuning of a student (Unsloth, or PEFT + TRL). |
| `eval_generation.py` | Runs the golden set through any OpenAI-compatible endpoint and scores it; keeps a comparison table across runs. |
| `requirements-train.txt` | Heavy dependencies of `train_lora.py` only. |
| `results/` | Committed result files. `retrieval_baseline.*` is real. |
| `tests/` | Unit tests, no network. |

`ml/data/` (teacher data) and `ml/runs/` (adapters, merged models) are
git-ignored.

## Golden set

Fields: `id`, `lang`, `question`, `answerable`, `source_id` (a document id of
`student_services.load_documents()`, `null` when unanswerable), `must_contain`
(strings copied verbatim from that document that a correct answer must
include), `needs_native_review`, and optionally:

- `parallel_of` — id of the English question this one translates. 33 of the 36
  non-English questions are translations, so languages can be compared on the
  same facts. Three zh-TW questions are written directly against the Chinese
  scholarship guidelines.
- `alt_source_ids` — other documents that state the same fact (for example the
  Cathay limits are on the insurance page and in the handbook). They count as
  hits and as correct citations. This field is an addition to the brief.

- `must_contain_any` — spellings of one fact, of which an answer needs only
  one; it is asked for on top of `must_contain`, and one of the spellings is
  the document's own. Two items have it, and **both were changed after reading
  model output, so they are no longer blind**: `zh-003` (`30` or `三十`; an
  answer of 「三十天」 had been marked wrong) and `en-005` (`excluded`,
  `not covered`, `does not cover`, `only hospitalization`; the earlier
  `hospitalization` failed "outpatient visits are completely excluded" and
  passed an answer that said outpatient visits are covered). `en-005` is a
  yes/no question and these strings are still only a proxy: a wrong "yes"
  that happens to use one of them passes. Read its answers by hand.

`must_contain` is mostly numbers and URLs so that it also works for answers in
other languages. A bare number must appear as a whole number (`20` is not found
in `2026`; `1,000`, `1000` and `1.000` are the same); other strings are matched
ignoring case, spaces and dash style. English-only questions also use English
phrases.

**The vi and id questions, and the vi and id refusal sentences in
`prompt_format.py`, are machine-drafted. A native speaker must review them
before any number computed on them is quoted.** They are marked
`needs_native_review: true`. The zh-TW questions were also written by the
model and deserve a read by a native speaker, although they are not flagged.

## Order of steps

All commands that touch the backend service are run from `backend/`
(`uv sync --frozen` once, if the environment does not exist yet).

### 1. Tests (laptop, no network)

```bash
cd backend && uv run --frozen pytest ../ml/tests -q
```

### 2. Retrieval on the golden set (laptop, no network)

```bash
cd backend && uv run --frozen python ../ml/eval_retrieval.py
```

Writes `ml/results/retrieval_baseline.md` and `.json`. Current result:

| lang | answerable | hit@1 | hit@3 | ans@3 | no result | unanswerable with passages |
|---|---|---|---|---|---|---|
| en | 21 | 17/21 (81%) | 21/21 (100%) | 19/21 (90%) | 0/21 (0%) | 2/3 (67%) |
| zh-TW | 10 | 9/10 (90%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| vi | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| id | 10 | 8/10 (80%) | 10/10 (100%) | 10/10 (100%) | 0/10 (0%) | 2/2 (100%) |
| all | 51 | 42/51 (82%) | 51/51 (100%) | 49/51 (96%) | 0/51 (0%) | 8/9 (89%) |

`ans@3` (the three returned passages hold the answer, by the same test as for
a model's answer: all `must_contain` strings and one of `must_contain_any`) is the
number that matters for the generator. hit@k is about documents, and there are
only 8, so it says little.

How the search got here, `ans@3` after each change (each row includes the rows
above it):

| search | en | zh-TW | vi | id | all | answerable, no result | unanswerable with passages |
|---|---|---|---|---|---|---|---|
| first version: substring match, one passage per document | 15/21 | 2/10 | 4/10 | 3/10 | 24/51 | 7/51 | 6/9 |
| several passages per document | 15/21 | 1/10 | 2/10 | 4/10 | 22/51 | 7/51 | 6/9 |
| Latin-script words matched whole | 16/21 | 2/10 | 2/10 | 4/10 | 24/51 | 11/51 | 4/9 |
| title matched with each passage, rare words weigh more, plural "s" ignored | 19/21 | 2/10 | 2/10 | 5/10 | 28/51 | 11/51 | 4/9 |
| relevance threshold | 19/21 | 0/10 | 0/10 | 1/10 | 20/51 | 28/51 | 2/9 |
| Chinese questions cut into bigrams | 19/21 | 4/10 | 0/10 | 1/10 | 24/51 | 20/51 | 3/9 |
| glossary of zh-TW / vi / id terms | 19/21 | 10/10 | 10/10 | 10/10 | 49/51 | 0/51 | 8/9 |

How to read it:

- **The golden set is the only test set, and the search was built while
  looking at it.** The scoring changes (third and fourth row) were chosen
  because they fixed English questions of this set. The threshold was fixed by
  a rule (below) but checked on the same 60 questions. So these are not
  held-out numbers; expect less on new questions.
- **The 30/30 for zh-TW, vi and id is optimistic.** The glossary was written
  from the topics of the 8 documents, not from the questions, but by the same
  kind of language model that drafted the vi and id questions (and 33 of the 36
  non-English questions translate English ones about the documents' main
  facts). A student who words a question differently from the glossary gets
  nothing. The vi and id glossary entries have not been reviewed by a native
  speaker.
- Whole-word matching and the threshold take away the vi and id results of the
  first version (4/10 and 3/10 down to 0/10 and 1/10 before the glossary).
  Those were matches on shared names and numbers and on substrings ("nhi" in
  "nhiêu" matching NHI); without the glossary the search now returns nothing
  for such a question instead.
- Threshold: a passage is returned when it has every word of the query, or
  when the weights of the words it has add up to the weight of a word found in
  a single passage (ln(1 + number of passages), 5.25 for the 190 passages
  now). On this set that drops the passages of 1 of the 9 unanswerable
  questions and of no answerable one; the lowest answerable question scores
  5.82, so the margin is thin, and a cut-off of 6 would already lose 4
  answerable questions. The unanswerable questions here are about neighbouring
  subjects (dependent visa, income tax) and score like answerable ones: the
  search still gives no usable "not covered" signal, and refusing remains the
  generator's job.
- With 51 answerable questions, differences of one or two questions per
  language mean nothing.
- Two English questions still miss: the list of documents they ask for is in a
  passage that overlaps a better-scoring one, and overlapping passages are not
  returned together.

### 3. Teacher data (teacher on Colab, script on the laptop)

Serve gpt-oss on Colab behind an OpenAI-compatible endpoint, then on the
laptop:

```bash
cd backend
uv run --frozen python ../ml/make_teacher_data.py --dry-run -n 40   # plumbing check, fake teacher

export TEACHER_BASE_URL=...   # ends in /v1
export TEACHER_API_KEY=...    # only if the endpoint needs one
export TEACHER_MODEL=...      # model name the endpoint expects
uv run --frozen python ../ml/make_teacher_data.py -n 2000 --out ../ml/data/train.jsonl
```

The scripts read the endpoint only from these environment variables and never
print the URL or the key.

- Passages are cut with the backend's `PASSAGE_LINES` / `PASSAGE_STEP`.
- An answer example shows the passage with the answer plus 1-2 distractor
  passages from other documents, in random order. (The brief says one
  distractor; a second is added at random because evaluation prompts hold up to
  three passages. `--max-distractors 1` restores exactly one.)
- About 18% are refusal examples (`--refusal-rate`): the teacher sees a hidden
  passage and writes a question it answers, the student only gets passages of
  other documents, and the target is the fixed refusal sentence of
  `prompt_format.py`, not teacher text.
- Language mix: `--lang-mix en=0.55,zh-TW=0.15,vi=0.15,id=0.15` (default).
- Cleaning (`clean_example`): reasoning channel removed (`<think>`, Gemma
  thought channel, gpt-oss harmony `analysis` channel; the API's separate
  reasoning field is never read); reply must be JSON; every bullet must cite;
  every `[n]` must be a shown passage and the answering passage must be cited;
  every number and English month name in the answer must occur in the shown
  passages; questions equal or similar (ratio >= 0.85 after normalization) to a
  golden question are dropped. Rejections go to `<out>.rejects.jsonl` with the
  reason.
- Resume: rerun the same command. Tasks already written or rejected are
  skipped; a larger `-n` continues the same plan. A request that fails is not
  recorded, so it is retried on the next run.
- More teachers: `--teachers TEACHER,TEACHER2` with `TEACHER2_BASE_URL`,
  `TEACHER2_API_KEY`, `TEACHER2_MODEL`. Tasks alternate between teachers and
  each row records `teacher`. To add a teacher to an existing file later, run
  it alone with a different `--seed` and the same `--out`.

Read a sample of the output before training. The cleaning catches invented
numbers and broken citations; it cannot tell whether an answer is otherwise
faithful, whether a "refusal" question is really unanswerable from the shown
passages, or whether a vi/id question is natural.

### 4. Training (laptop with RTX 4070 8 GB, or Colab)

```bash
python -m venv .venv-train && . .venv-train/bin/activate   # not the backend environment
pip install -r ml/requirements-train.txt
python ml/train_lora.py --check-data --data ml/data/train.jsonl
python ml/train_lora.py --data ml/data/train.jsonl \
    --model aisingapore/Gemma-SEA-LION-v4.5-E2B-IT --output ml/runs/sealion-e2b
python ml/train_lora.py --data ml/data/train.jsonl \
    --model google/gemma-4-E2B-it --output ml/runs/gemma4-e2b
python ml/train_lora.py --data ml/data/train.jsonl \
    --model google/gemma-3-1b-it --output ml/runs/gemma3-1b    # gated: set HF_TOKEN
```

Defaults: 4-bit loading, batch size 1, gradient accumulation 8, sequence length
2048, LoRA rank 16, 3 epochs, loss on the assistant turn only, vision layers
not trained. `--backend peft` uses transformers + PEFT + TRL instead of Unsloth.
`--merge` also writes merged 16-bit weights; `--gguf q4_k_m` exports a GGUF
(Unsloth backend).

`--model` also takes Unsloth's mirrors `unsloth/gemma-4-E2B-it`,
`unsloth/gemma-3-1b-it` and `unsloth/gemma-3-270m-it`, which are not gated.

Notes from the runs on Colab. They were reported by the session that ran
them and have not been repeated on another machine:

- **Batch size.** The defaults (batch 1, accumulation 8) are for 8 GB of GPU
  memory. On Colab use `--batch-size 8 --grad-accum 1`: the same effective
  batch, about 3.9 s per step with the defaults and about 6.6 times faster
  this way.
- **Gemma 3 through Unsloth for inference.** Load it with
  `attn_implementation="eager"`. Nothing in this repository loads a model for
  inference (`eval_generation.py` talks to a server), so this concerns the
  notebook that scores a model directly with transformers.
- **Repeatable scores.** For the same reason the settings are not in a script
  here: when a model is scored directly on a GPU, set
  `torch.use_deterministic_algorithms(True, warn_only=True)` and the
  environment variable `CUBLAS_WORKSPACE_CONFIG=:4096:8` before the first
  CUDA call, so that two runs of the same model give the same answers.
- **270m and 1B were scored in bf16**, not from a quantised GGUF, so their
  numbers are not the numbers of the file that would be served. Numbers from
  different GPU types are not comparable either: compare runs made on the same
  machine with the same settings.

Manual GGUF export from `<output>/merged` with llama.cpp:

```bash
git clone https://github.com/ggml-org/llama.cpp
python llama.cpp/convert_hf_to_gguf.py ml/runs/sealion-e2b/merged \
    --outfile sealion-e2b-tuned-f16.gguf --outtype f16
# then quantize with llama.cpp's llama-quantize, e.g. to Q4_K_M
```

`train_lora.py` was written on a machine without a GPU, where only
`--check-data` and `--help` were executed. It was then run on Colab on
2026-11-12 (G4 and A100) for Gemma 3 270m and 1B and for Gemma 4 E2B,
according to the session that did that work; this has not been repeated on
another machine. See "Not tested" below.

### 5. Serve a student

Any OpenAI-compatible server works, for example llama.cpp:

```bash
llama-server -m sealion-e2b-tuned-q4_k_m.gguf --port 8080 --jinja
```

Serve the base model the same way to get the "before" numbers. The server must
apply the model's own chat template; a different template than in training
makes a tuned model look worse than it is.

### 6. Generation evaluation (laptop)

```bash
cd backend
uv run --frozen python ../ml/eval_generation.py --dry-run          # plumbing check, fake model

export EVAL_BASE_URL=http://127.0.0.1:8080/v1
export EVAL_MODEL=sealion-e2b-tuned
uv run --frozen python ../ml/eval_generation.py --run-name sealion-e2b-tuned
uv run --frozen python ../ml/eval_generation.py --run-name sealion-e2b-tuned-oracle --passages oracle
```

Run it once per model (teacher, each base student, each tuned student); use
`--endpoint TEACHER` to evaluate the teacher with the `TEACHER_*` variables.
Every run is saved in `ml/results/generation/<run-name>.json` and
`ml/results/generation_comparison.md` is rebuilt from all of them.

Columns, per language:

| Column | Meaning |
|---|---|
| answer | answerable questions answered with every `must_contain` string and, where listed, one of `must_contain_any` |
| answer (ctx) | the same, only over questions whose prompt passages hold the answer by that same test (a number has to be a whole number there too: `20` is not in `std20230311`) |
| cite | answerable questions answered with valid `[n]`, at least one pointing to a passage of the expected document |
| refuse | unanswerable questions that were refused |
| refuse (no ctx) | answerable questions whose passages lack the answer and that were refused (correct behaviour) |
| over-refuse | passages hold the answer but the model refused |
| num-err | replies containing a number that is neither in the passages nor in the question |

A refusal is a reply that contains the OIA e-mail address and no `[n]`.
With `--passages retrieved`, `answer` is capped by retrieval (`ans@3` above);
`answer (ctx)` and the oracle run show what the model itself can do.

## What has been tested

Run on the development machine (no GPU, no network access to a model):

- `pytest ml/tests`: 201 passed.
- `eval_retrieval.py`: real run, numbers above.
- `make_teacher_data.py --dry-run`, including resume and a larger `-n`.
- `eval_generation.py --dry-run`, both `--passages` modes.
- `train_lora.py --check-data` and `--help`.

## Not tested

- **No real endpoint was ever called.** `ChatClient`, the teacher prompts and
  the JSON reply format have not been tried against gpt-oss or any other model.
  Expect to adjust `--max-tokens` (a reasoning model spends tokens on thinking
  before the JSON) and possibly the teacher prompt.
- **`train_lora.py` was not executed where it was written**, in either
  backend; the runs on Colab (see "Training") came later and are known here
  only from that session's report. Points that were uncertain when it was
  written:
  - whether Unsloth's `FastModel` loads `aisingapore/Gemma-SEA-LION-v4.5-E2B-IT`
    (Unsloth documents its own `unsloth/gemma-4-E2B-it` mirror) and whether
    replacing its chat template with Unsloth's `gemma-4` template is harmless;
  - whether `train_on_responses_only` with `<|turn>user\n` / `<|turn>model\n`
    masks correctly when the conversation starts with a system turn (check that
    a decoded label sequence contains only the answer before a long run);
  - Gemma 3's template is believed to fold the system prompt into the first
    user turn; not checked here;
  - in the PEFT backend: the `dtype=` argument of `from_pretrained`, PEFT's
    default target modules for Gemma 4, `optim="adamw_torch_fused"` with 4-bit
    weights, and whether an 8 GB card fits it at all. Google's guide also saves
    `lm_head` and `embed_tokens` (`modules_to_save`); this script does not,
    because no tokens are added and it would not fit in 8 GB;
  - `requirements-train.txt` was never installed; Unsloth may conflict with the
    minimum versions listed there;
  - memory: Unsloth states 8-10 GB for E2B LoRA, so 8 GB is at the edge even in
    4 bit. If it runs out, lower `--max-seq-length` first, or train on Colab;
  - merging and GGUF export, including whether llama.cpp's converter handles a
    merged Gemma 4 E2B checkpoint that still contains the vision and audio
    towers.
- The golden set has not been reviewed by anyone but its author; vi and id
  need a native speaker.
- The dry-run numbers of both scripts come from fake models and mean nothing.

## Documentation used for `train_lora.py` (read 2026-11-10)

- Unsloth, Gemma 4 fine-tuning: <https://unsloth.ai/docs/models/gemma-4/train>
  — `FastModel.from_pretrained` / `get_peft_model` arguments
  (`finetune_vision_layers=False` for text only), chat template `gemma-4`
  (non-thinking, recommended for E2B/E4B), `train_on_responses_only` with
  `<|turn>user\n` and `<|turn>model\n`, removing the leading `<bos>` (wrong for
  Gemma 4 in practice: its processor does not add `<bos>` again, so the script
  keeps it; see `strip_bos` in `train_lora.py`),
  batch size 1 + gradient accumulation, `adamw_8bit`, E2B LoRA at 8-10 GB,
  a loss of 13-15 being normal for the multimodal E2B/E4B.
- Unsloth notebooks (Gemma 4 E2B/E4B text, Gemma 3) via Context7
  `/unslothai/notebooks`: <https://github.com/unslothai/notebooks> — the same
  calls, and the `gemma-3` template name.
- Unsloth, saving to GGUF:
  <https://unsloth.ai/docs/basics/inference-and-deployment/saving-to-gguf> —
  `save_pretrained_merged(..., save_method="merged_16bit")`,
  `save_pretrained_gguf(..., quantization_method="q4_k_m")`,
  `convert_hf_to_gguf.py`.
- Google, Gemma QLoRA with Hugging Face (now targets Gemma 4):
  <https://ai.google.dev/gemma/docs/core/huggingface_text_finetune_qlora> —
  `transformers>=5.10.1`, `peft>=0.19.0`, `AutoModelForMultimodalLM`,
  `BitsAndBytesConfig` nf4 + double quantization, prompt/completion dataset
  with loss on the completion, `merge_and_unload`, end-of-turn token `<turn|>`.
- TRL `SFTTrainer` docs via Context7 `/huggingface/trl`:
  <https://github.com/huggingface/trl/blob/main/docs/source/sft_trainer.md> —
  conversational prompt/completion format, `completion_only_loss` (default
  true), `max_length`, `peft_config`, `processing_class`.
- Model cards: <https://huggingface.co/google/gemma-4-E2B-it> (system role is
  native, thinking is off unless enabled, `AutoModelForMultimodalLM`),
  <https://huggingface.co/aisingapore/Gemma-SEA-LION-v4.5-E2B-IT> (MIT,
  fine-tune of `google/gemma-4-E2B-it`), `google/gemma-3-1b-it` (gated,
  text-only `AutoModelForCausalLM`).

## Demo page

`demo/app.py` serves one page that shows the whole chain for a question typed
in any of the four languages. The chain itself is the backend's
(`backend/app/services/assistant.py`, also served as `POST /api/v1/assistant/ask`);
the page only shows each step: where the question was sent (OIA documents, a
campus building, or live bus arrivals), the passages the model was given, the
model's answer, and two automatic checks on it (the cited passage numbers
exist; every number in the answer appears in the passages).

```bash
cd backend
# look-up only
uv run --frozen python ../ml/demo/app.py --port 8765
# with a model behind an OpenAI-compatible endpoint, e.g. Ollama
DEMO_BASE_URL=http://localhost:11434/v1 DEMO_MODEL=<model name> \
  DEMO_REASONING_EFFORT=none \
  uv run --frozen python ../ml/demo/app.py --port 8765
```

With a model, a question that is not English is first translated by the same
model, because the pages are in English; the documents are then searched for
the words of the translation and of the question. Without a model the page
shows what was looked up only, and a question in another language finds the
English pages through the search's glossary alone. Bus
questions need the backend's `TDX_CLIENT_ID` and `TDX_CLIENT_SECRET`. Live
arrivals are shown as data and never go through the model: tried on 2026-11-11,
the untuned model gave the time the data was read as the bus time and answered
Vietnamese and Indonesian bus questions in Thai script. It listens on 127.0.0.1; it
has no login and is not meant to be exposed.

Run on 2026-11-11 with an untuned `unsloth/gemma-4-E2B-it-GGUF` (Q4_K_M) behind
Ollama on a CPU-only server, all 60 golden questions through the page's API,
look-up and answer together (one run, temperature 0):

| lang | right document in top 3 | answer passage in top 3 | answer correct | refused when unanswerable |
|---|---|---|---|---|
| en | 21/21 | 15/21 | 14/21 | 2/3 |
| zh-TW | 7/10 | 5/10 | 4/10 | 2/2 |
| vi | 10/10 | 8/10 | 7/10 | 2/2 |
| id | 10/10 | 8/10 | 7/10 | 2/2 |

No answer contained a number missing from its passages or cited a passage that
was not there. Read these with care: "answer correct" only checks that the
expected figures appear; the translation prompt was chosen after three tries
on these same 60 questions, so the non-English rows are optimistic; and there
are ten questions per non-English language. All 60 are document questions and
none of them was sent to the building or bus look-up. Building and bus
questions have no golden set yet; they were only tried by hand.
