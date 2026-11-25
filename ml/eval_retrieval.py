"""The backend's knowledge search, scored on the golden questions.

Run from the backend directory so its dependencies are available:

    cd backend && uv run --frozen python ../ml/eval_retrieval.py --search keyword
    EMBEDDING_BASE_URL=... EMBEDDING_MODEL=... \
        uv run --frozen python ../ml/eval_retrieval.py --search hybrid

`--search keyword` is `knowledge.index.search` without an embedder; `hybrid`
embeds every passage through the endpoint named by EMBEDDING_BASE_URL and
EMBEDDING_MODEL (stored in a SQLite file) and fuses both rankings.

Per language, for answerable questions:
  hit@1 / hit@3  the expected document (`source_id`, or one of `alt_source_ids`)
                 is the first / among the first three results
  ans@3          the text of the first three passages holds the answer as
                 `checks.holds_answer` tests it: every `must_contain` string
                 and one spelling of `must_contain_any` (passages are 8 lines
                 long, so the right document does not guarantee the right
                 passage)
  ans@5          the same for the first five passages
and for unanswerable questions how often the search still returns passages.
Items without `source_type` are the first 60 questions ("old"); the others
are written against the larger corpus ("new").
`--sweep 0.3,0.4,...` repeats the run with those values of
KNOWLEDGE_MIN_SIMILARITY. Writes ml/results/retrieval_<search>.md and .json;
with `--golden` set to another file, the golden file's stem is appended to the
names (retrieval_<search>_<stem>.md) so the results of the golden set are kept.
"""

import argparse
import json
from pathlib import Path

from checks import holds_answer
from common import (
    GOLDEN_PATH,
    RESULTS_DIR,
    expected_sources,
    knowledge_services,
    load_golden,
    markdown_table,
    rate,
)
from prompt_format import LANGS

TOP_K = 5
SETS = ("old", "new", "all")


def item_set(item: dict) -> str:
    return "new" if "source_type" in item else "old"


def summarize(items: list[dict]) -> dict:
    summary = {}
    for name in SETS:
        summary[name] = {}
        for lang in (*LANGS, "all"):
            chosen = [
                row
                for row in items
                if name in (row["set"], "all") and lang in (row["lang"], "all")
            ]
            answerable = [row for row in chosen if row["answerable"]]
            unanswerable = [row for row in chosen if not row["answerable"]]
            summary[name][lang] = {
                "answerable": len(answerable),
                "hit1": sum(row["hit1"] for row in answerable),
                "hit3": sum(row["hit3"] for row in answerable),
                "ans3": sum(row["ans3"] for row in answerable),
                "ans5": sum(row["ans5"] for row in answerable),
                "no_result": sum(not row["returned"] for row in answerable),
                "unanswerable": len(unanswerable),
                "unanswerable_with_passages": sum(
                    bool(row["returned"]) for row in unanswerable
                ),
            }
    return summary


def by_source_type(items: list[dict]) -> dict:
    found = {}
    for row in items:
        if row["set"] != "new" or not row["answerable"]:
            continue
        entry = found.setdefault(
            row["source_type"], {"answerable": 0, "ans3": 0, "ans5": 0}
        )
        entry["answerable"] += 1
        entry["ans3"] += row["ans3"]
        entry["ans5"] += row["ans5"]
    return found


def evaluate(golden: list[dict], search) -> dict:
    items = []
    for item in golden:
        results = search(item["question"], limit=TOP_K)
        ids = [result["id"] for result in results]
        first_three = "\n".join(result["passage"] for result in results[:3])
        text = "\n".join(result["passage"] for result in results)
        expected = expected_sources(item)
        similarities = [
            result["similarity"]
            for result in results
            if result.get("similarity") is not None
        ]
        items.append(
            {
                "id": item["id"],
                "lang": item["lang"],
                "set": item_set(item),
                "source_type": item.get("source_type"),
                "answerable": item["answerable"],
                "returned": ids,
                "hit1": bool(expected & set(ids[:1])),
                "hit3": bool(expected & set(ids[:3])),
                "ans3": item["answerable"] and holds_answer(first_three, item),
                "ans5": item["answerable"] and holds_answer(text, item),
                "top_similarity": max(similarities, default=None),
            }
        )
    return {
        "top_k": TOP_K,
        "summary": summarize(items),
        "by_source_type": by_source_type(items),
        "items": items,
    }


def to_markdown(report: dict) -> str:
    sections = []
    for name in SETS:
        rows = []
        for lang, s in report["summary"][name].items():
            rows.append(
                [
                    lang,
                    s["answerable"],
                    rate(s["hit1"], s["answerable"]),
                    rate(s["hit3"], s["answerable"]),
                    rate(s["ans3"], s["answerable"]),
                    rate(s["ans5"], s["answerable"]),
                    rate(s["no_result"], s["answerable"]),
                    rate(s["unanswerable_with_passages"], s["unanswerable"]),
                ]
            )
        table = markdown_table(
            [
                "lang",
                "answerable",
                "hit@1",
                "hit@3",
                "ans@3",
                "ans@5",
                "no result",
                "unanswerable with passages",
            ],
            rows,
        )
        sections.append(f"### {name} questions\n\n{table}")
    types = [
        [
            source_type,
            e["answerable"],
            rate(e["ans3"], e["answerable"]),
            rate(e["ans5"], e["answerable"]),
        ]
        for source_type, e in sorted(report["by_source_type"].items())
    ]
    sections.append(
        "### new questions by source type\n\n"
        + markdown_table(["source type", "answerable", "ans@3", "ans@5"], types)
    )
    return "\n\n".join(sections)


def sweep_markdown(rows: list[dict]) -> str:
    table = [
        [
            row["min_similarity"],
            rate(row["old"]["all"]["ans3"], row["old"]["all"]["answerable"]),
            rate(row["new"]["all"]["ans3"], row["new"]["all"]["answerable"]),
            rate(row["all"]["all"]["ans3"], row["all"]["all"]["answerable"]),
            rate(row["all"]["all"]["ans5"], row["all"]["all"]["answerable"]),
            rate(
                row["all"]["all"]["unanswerable_with_passages"],
                row["all"]["all"]["unanswerable"],
            ),
            row["vector_hits_unanswerable"],
        ]
        for row in rows
    ]
    return markdown_table(
        [
            "KNOWLEDGE_MIN_SIMILARITY",
            "ans@3 old",
            "ans@3 new",
            "ans@3 all",
            "ans@5 all",
            "unanswerable with passages",
            "unanswerable with a vector hit",
        ],
        table,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--search", choices=("keyword", "hybrid"), default="keyword")
    parser.add_argument("--min-similarity", type=float)
    parser.add_argument(
        "--sweep", help="comma-separated KNOWLEDGE_MIN_SIMILARITY values"
    )
    parser.add_argument("--out-dir", type=Path, default=RESULTS_DIR)
    parser.add_argument(
        "--golden", type=Path, default=GOLDEN_PATH, help="questions JSONL to score"
    )
    args = parser.parse_args()
    suffix = "" if args.golden == GOLDEN_PATH else f"_{args.golden.stem}"
    questions_file = f"ml/golden/{args.golden.name}"

    services = knowledge_services(args.search)
    from app.core.config import settings

    golden = load_golden(args.golden)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    if args.sweep:
        if args.search != "hybrid":
            parser.error("--sweep needs --search hybrid")
        rows = []
        for value in [float(v) for v in args.sweep.split(",")]:
            settings.KNOWLEDGE_MIN_SIMILARITY = value
            report = evaluate(golden, services.search)
            hits = sum(
                row["top_similarity"] is not None
                for row in report["items"]
                if not row["answerable"]
            )
            rows.append(
                {
                    "min_similarity": value,
                    **report["summary"],
                    "vector_hits_unanswerable": hits,
                }
            )
        table = sweep_markdown(rows)
        print(table)
        (args.out_dir / f"retrieval_hybrid_sweep{suffix}.md").write_text(
            "# KNOWLEDGE_MIN_SIMILARITY sweep\n\nHybrid search, top 3 and 5, "
            f"`{questions_file}`. Produced by "
            "`ml/eval_retrieval.py --sweep`.\n\n" + table + "\n",
            encoding="utf-8",
        )
        return
    if args.min_similarity is not None:
        settings.KNOWLEDGE_MIN_SIMILARITY = args.min_similarity

    report = evaluate(golden, services.search)
    report["search"] = args.search
    report["min_similarity"] = settings.KNOWLEDGE_MIN_SIMILARITY
    table = to_markdown(report)
    print(table)

    (args.out_dir / f"retrieval_{args.search}{suffix}.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    detail = (
        "Keyword search"
        if args.search == "keyword"
        else "Hybrid search (keyword and embeddings, fused)"
    )
    (args.out_dir / f"retrieval_{args.search}{suffix}.md").write_text(
        "# Retrieval on the golden set\n\n"
        f"{detail} (`knowledge.index.search`, top 3 and 5, no source-type filter) on "
        f"`{questions_file}`. Produced by `ml/eval_retrieval.py "
        f"--search {args.search}`; column meanings are in its docstring.\n\n"
        f"{table}\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
