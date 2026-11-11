"""The backend's keyword passage search, scored on the golden questions.

Run from the backend directory so its dependencies are available:

    cd backend && uv run --frozen python ../ml/eval_retrieval.py

Per language, for answerable questions:
  hit@1 / hit@3  the expected document (`source_id`, or one of `alt_source_ids`)
                 is the first / among the first three results
  ans@3          every `must_contain` string is in the text of the first three
                 passages (passages are 8 lines long, so the right document
                 does not guarantee the right passage)
and for unanswerable questions how often the search still returns passages.
The search's relevance threshold and its glossary were written with these 60
questions as the only test set; ml/README.md says what that does to the numbers.
Writes ml/results/retrieval_baseline.md and .json.
"""

import argparse
import json
from pathlib import Path

from common import (
    RESULTS_DIR,
    expected_sources,
    load_golden,
    markdown_table,
    rate,
    student_services,
)
from prompt_format import LANGS

TOP_K = 3


def evaluate(golden: list[dict], search) -> dict:
    items = []
    for item in golden:
        results = search(item["question"], limit=TOP_K)
        ids = [result["id"] for result in results]
        text = "\n".join(result["passage"] for result in results)
        expected = expected_sources(item)
        items.append(
            {
                "id": item["id"],
                "lang": item["lang"],
                "answerable": item["answerable"],
                "returned": ids,
                "hit1": bool(expected & set(ids[:1])),
                "hit3": bool(expected & set(ids)),
                "ans3": item["answerable"]
                and all(fact in text for fact in item["must_contain"]),
            }
        )
    summary = {}
    for lang in (*LANGS, "all"):
        chosen = [row for row in items if lang in (row["lang"], "all")]
        answerable = [row for row in chosen if row["answerable"]]
        unanswerable = [row for row in chosen if not row["answerable"]]
        summary[lang] = {
            "answerable": len(answerable),
            "hit1": sum(row["hit1"] for row in answerable),
            "hit3": sum(row["hit3"] for row in answerable),
            "ans3": sum(row["ans3"] for row in answerable),
            "no_result": sum(not row["returned"] for row in answerable),
            "unanswerable": len(unanswerable),
            "unanswerable_with_passages": sum(
                bool(row["returned"]) for row in unanswerable
            ),
        }
    return {"top_k": TOP_K, "summary": summary, "items": items}


def to_markdown(report: dict) -> str:
    rows = []
    for lang, s in report["summary"].items():
        rows.append(
            [
                lang,
                s["answerable"],
                rate(s["hit1"], s["answerable"]),
                rate(s["hit3"], s["answerable"]),
                rate(s["ans3"], s["answerable"]),
                rate(s["no_result"], s["answerable"]),
                rate(s["unanswerable_with_passages"], s["unanswerable"]),
            ]
        )
    return markdown_table(
        [
            "lang",
            "answerable",
            "hit@1",
            "hit@3",
            "ans@3",
            "no result",
            "unanswerable with passages",
        ],
        rows,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out-dir", type=Path, default=RESULTS_DIR)
    args = parser.parse_args()

    services = student_services()
    report = evaluate(load_golden(), services.search)
    table = to_markdown(report)
    print(table)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "retrieval_baseline.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (args.out_dir / "retrieval_baseline.md").write_text(
        "# Retrieval on the golden set\n\n"
        "Backend keyword search (`student_services.search`, top 3, no topic "
        "filter) on `ml/golden/questions.jsonl`. Produced by "
        "`ml/eval_retrieval.py`; column meanings are in its docstring.\n\n"
        f"{table}\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
