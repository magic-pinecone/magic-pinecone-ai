"""Run the golden questions through a model and score the answers.

    cd backend
    # no network, built-in fake model, writes to a temporary directory:
    uv run --frozen python ../ml/eval_generation.py --dry-run
    # any OpenAI-compatible endpoint (teacher, llama.cpp server with a student):
    EVAL_BASE_URL=http://127.0.0.1:8080/v1 EVAL_MODEL=sealion-e2b-base \\
        uv run --frozen python ../ml/eval_generation.py --run-name sealion-e2b-base

The prompt is built by `prompt_format` from the passages the backend's search
really returns for the question (`--passages retrieved`, the default). With
`--passages oracle` the passage of the expected document that holds the answer
is put into the prompt, which measures the model apart from the retriever.

Scores per language (definitions in `score_item` and `summarize`):
  answer    answerable questions whose answer has every `must_contain` string
  answer (ctx)  the same, only over questions whose passages hold those strings
  cite      answerable questions answered with valid citations, at least one
            to a passage of the expected document
  refuse    unanswerable questions that were refused
  refuse (no ctx)  answerable questions whose passages lack the answer and that
            were refused (the right behaviour there)
  over-refuse  questions whose passages hold the answer but were refused
  num-err   replies with a number found neither in the passages nor in the
            question

Each run is saved as `ml/results/generation/<run-name>.json` and
`ml/results/generation_comparison.md` is rebuilt from all saved runs.
"""

import argparse
import json
import tempfile
from pathlib import Path

from checks import contains_fact, strip_reasoning, unsupported_numbers
from common import (
    RESULTS_DIR,
    ChatClient,
    EndpointError,
    expected_sources,
    load_golden,
    markdown_table,
    rate,
    student_services,
)
from prompt_format import (
    LANGS,
    OIA_CONTACT_STRINGS,
    build_messages,
    cited_numbers,
    is_refusal,
    refusal_text,
)

METRICS = (
    # (column, numerator key, denominator key)
    ("answer", "answer_ok", "answerable"),
    ("answer (ctx)", "answer_ok_ctx", "ctx_has_answer"),
    ("cite", "citation_ok", "answerable"),
    ("refuse", "refused_unanswerable", "unanswerable"),
    ("refuse (no ctx)", "refused_no_ctx", "ctx_lacks_answer"),
    ("over-refuse", "over_refused", "ctx_has_answer"),
    ("num-err", "number_error", "total"),
)


def oracle_passage(services, item: dict) -> dict:
    """The passage of the expected document holding most `must_contain` strings."""
    document = next(d for d in services.load_documents() if d.id == item["source_id"])
    best, best_score = "", -1
    for start in range(0, max(1, len(document.lines)), services.PASSAGE_STEP):
        text = "\n".join(document.lines[start : start + services.PASSAGE_LINES])
        score = sum(fact in text for fact in item["must_contain"])
        if score > best_score:
            best, best_score = text, score
    return {"id": document.id, "title": document.title, "passage": best}


def select_passages(services, item: dict, mode: str, top_k: int) -> list[dict]:
    found = [
        {"id": r["id"], "title": r["title"], "passage": r["passage"]}
        for r in services.search(item["question"], limit=top_k)
    ]
    if mode == "retrieved" or not item["answerable"]:
        return found
    gold = oracle_passage(services, item)
    others = [p for p in found if p["id"] != gold["id"]][: top_k - 1]
    # A fixed but question-dependent position, so the answer is not always [1].
    position = sum(map(ord, item["id"])) % (len(others) + 1)
    return [*others[:position], gold, *others[position:]]


def score_item(item: dict, passages: list[dict], response: str) -> dict:
    """Flags for one reply; `response` is already free of reasoning text."""
    context = "\n".join(p["title"] + "\n" + p["passage"] for p in passages)
    refused = is_refusal(response)
    cited = cited_numbers(response)
    valid = bool(cited) and all(1 <= n <= len(passages) for n in cited)
    expected = expected_sources(item)
    ctx_has_answer = item["answerable"] and all(
        fact in context for fact in item["must_contain"]
    )
    answer_ok = (
        item["answerable"]
        and not refused
        and all(contains_fact(response, fact) for fact in item["must_contain"])
    )
    wrong_numbers = unsupported_numbers(
        response, context + "\n" + item["question"], ignore=OIA_CONTACT_STRINGS
    )
    return {
        "refused": refused,
        "ctx_has_answer": ctx_has_answer,
        "answer_ok": answer_ok,
        "citation_ok": item["answerable"]
        and not refused
        and valid
        and any(passages[n - 1]["id"] in expected for n in cited),
        "unsupported_numbers": wrong_numbers,
    }


def summarize(records: list[dict]) -> dict:
    summary = {}
    for lang in (*LANGS, "all"):
        rows = [r for r in records if lang in (r["lang"], "all")]
        answerable = [r for r in rows if r["answerable"]]
        with_ctx = [r for r in answerable if r["ctx_has_answer"]]
        without_ctx = [r for r in answerable if not r["ctx_has_answer"]]
        unanswerable = [r for r in rows if not r["answerable"]]
        summary[lang] = {
            "total": len(rows),
            "answerable": len(answerable),
            "unanswerable": len(unanswerable),
            "ctx_has_answer": len(with_ctx),
            "ctx_lacks_answer": len(without_ctx),
            "answer_ok": sum(r["answer_ok"] for r in answerable),
            "answer_ok_ctx": sum(r["answer_ok"] for r in with_ctx),
            "citation_ok": sum(r["citation_ok"] for r in answerable),
            "refused_unanswerable": sum(r["refused"] for r in unanswerable),
            "refused_no_ctx": sum(r["refused"] for r in without_ctx),
            "over_refused": sum(r["refused"] for r in with_ctx),
            "number_error": sum(bool(r["unsupported_numbers"]) for r in rows),
        }
    return summary


def run_table(summary: dict) -> str:
    rows = [
        [lang, *(rate(s[top], s[bottom]) for _, top, bottom in METRICS)]
        for lang, s in summary.items()
    ]
    return markdown_table(["lang", *(name for name, _, _ in METRICS)], rows)


def comparison_markdown(runs: list[dict]) -> str:
    """One row per run and language, all saved runs side by side."""
    rows = []
    for run in sorted(runs, key=lambda r: (r["passages"], r["run_name"])):
        for lang, s in run["summary"].items():
            rows.append(
                [
                    run["run_name"],
                    run["passages"],
                    lang,
                    *(rate(s[top], s[bottom]) for _, top, bottom in METRICS),
                ]
            )
    table = markdown_table(
        ["run", "passages", "lang", *(name for name, _, _ in METRICS)], rows
    )
    return (
        "# Generation comparison\n\n"
        "Rebuilt by `ml/eval_generation.py` from `ml/results/generation/*.json`; "
        "column meanings are in its docstring. Compare runs only within the "
        "same `passages` mode.\n\n" + table + "\n"
    )


class FakeModel:
    """Offline stand-in for --dry-run: quotes one passage line, or refuses.

    It refuses when no passage shares a word of four or more letters with the
    question. The scores it gets say nothing about any real model.
    """

    name = "fake-model"

    def chat(self, messages: list[dict], **_: object) -> str:
        user = messages[-1]["content"]
        body, _, question = user.rpartition("\n\nQuestion: ")
        words = {w for w in question.casefold().split() if len(w) >= 4}
        best, best_score, number = "", 0, 0
        for block in body.removeprefix("Passages:\n").split("\n\n"):
            if not block.startswith("["):
                continue
            current = int(block[1 : block.index("]")])
            for line in block.split("\n")[1:]:
                score = sum(word in line.casefold() for word in words)
                if score > best_score:
                    best, best_score, number = line, score, current
        if not best:
            lang = "zh-TW" if any("一" <= c <= "鿿" for c in question) else "en"
            return "<think>nothing relevant</think>" + refusal_text(lang)
        return f"<think>line found</think>- {best.strip()} [{number}]"


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__.split("\n")[0],
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--run-name", help="label of this run in the comparison")
    parser.add_argument("--endpoint", default="EVAL", help="environment prefix")
    parser.add_argument(
        "--passages", choices=("retrieved", "oracle"), default="retrieved"
    )
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--max-tokens", type=int, default=512)
    parser.add_argument("--limit", type=int, help="only the first N questions")
    parser.add_argument("--out-dir", type=Path, help="default: ml/results")
    parser.add_argument("--dry-run", action="store_true", help="use the fake model")
    args = parser.parse_args()

    if args.dry_run:
        model = FakeModel()
        run_name = args.run_name or "dry-run"
        out_dir = args.out_dir or Path(tempfile.mkdtemp(prefix="generation-dry-run-"))
    else:
        if not args.run_name:
            parser.error("--run-name is required unless --dry-run is given")
        try:
            model = ChatClient(args.endpoint)
        except EndpointError as error:
            parser.error(str(error))
        run_name, out_dir = args.run_name, args.out_dir or RESULTS_DIR

    services = student_services()
    golden = load_golden()[: args.limit]
    records = []
    for number, item in enumerate(golden, start=1):
        passages = select_passages(services, item, args.passages, args.top_k)
        try:
            raw = model.chat(
                build_messages(item["question"], passages),
                temperature=args.temperature,
                max_tokens=args.max_tokens,
            )
        except EndpointError as error:
            raise SystemExit(f"stopped at {item['id']}: {error}") from None
        response = strip_reasoning(raw)
        records.append(
            {
                "id": item["id"],
                "lang": item["lang"],
                "answerable": item["answerable"],
                "passage_ids": [p["id"] for p in passages],
                "response": response,
                **score_item(item, passages, response),
            }
        )
        if number % 20 == 0 or number == len(golden):
            print(f"  {number}/{len(golden)} answered")

    run = {
        "run_name": run_name,
        "model": model.name,
        "passages": args.passages,
        "top_k": args.top_k,
        "questions": len(records),
        "summary": summarize(records),
        "items": records,
    }
    runs_dir = out_dir / "generation"
    runs_dir.mkdir(parents=True, exist_ok=True)
    (runs_dir / f"{run_name}.json").write_text(
        json.dumps(run, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    runs = [
        json.loads(path.read_text(encoding="utf-8"))
        for path in sorted(runs_dir.glob("*.json"))
    ]
    comparison = out_dir / "generation_comparison.md"
    comparison.write_text(comparison_markdown(runs), encoding="utf-8")

    print(f"\nrun {run_name} ({model.name}, passages={args.passages})\n")
    print(run_table(run["summary"]))
    print(f"\nwrote {runs_dir / (run_name + '.json')} and {comparison}")
    if args.dry_run:
        print("DRY RUN: fake model, these numbers say nothing about a real model.")


if __name__ == "__main__":
    main()
