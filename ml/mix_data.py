import argparse
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from checks import matches_golden, normalize_question
from common import (
    ChatClient,
    EndpointError,
    append_jsonl,
    held_out_questions,
    read_jsonl,
)
from make_teacher_data import (
    DEFAULT_VERIFY_VOTES,
    FakeTeacher,
    refusal_confirmed_by_votes,
)
from prompt_format import SYSTEM_PROMPT

QUESTION_MARK = "\n\nQuestion: "


def split_user(content: str) -> tuple[str, str]:
    passages, _, question = content.rpartition(QUESTION_MARK)
    return passages.removeprefix("Passages:\n"), question


def is_hard_refusal(row: dict) -> bool:
    return row.get("kind") == "refusal" and row.get("distractors") == "retrieved"


def with_current_prompt(row: dict) -> dict:
    messages = [dict(message) for message in row["messages"]]
    messages[0]["content"] = SYSTEM_PROMPT
    return {**row, "messages": messages}


def mix(
    rows: list[dict], golden: list[str], recheck, votes: int, workers: int = 1
) -> tuple[list[dict], list[dict]]:
    seen: set[str] = set()
    kept, rejected, pending = [], [], []
    for row in rows:
        if row["id"] in seen:
            rejected.append({"id": row["id"], "reason": "duplicate_id"})
            continue
        seen.add(row["id"])
        _, question = split_user(row["messages"][1]["content"])
        if matches_golden(normalize_question(question), golden):
            rejected.append({"id": row["id"], "reason": "matches_golden_question"})
        elif votes > 0 and is_hard_refusal(row):
            pending.append(row)
        else:
            kept.append(with_current_prompt(row))

    def check(row: dict) -> tuple[bool, str]:
        passages, question = split_user(row["messages"][1]["content"])
        return refusal_confirmed_by_votes(recheck, passages, question, votes)

    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        for row, (confirmed, reason) in zip(
            pending, pool.map(check, pending), strict=True
        ):
            if confirmed:
                kept.append(with_current_prompt(row))
            else:
                rejected.append({"id": row["id"], "reason": reason})
    return kept, rejected


def counts(rows: list[dict], field: str) -> dict:
    result: dict = {}
    for row in rows:
        result[row.get(field)] = result.get(row.get(field), 0) + 1
    return dict(sorted(result.items(), key=lambda item: str(item[0])))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Merge teacher-data files into one training file with the "
        "current system prompt; drop golden look-alikes and recheck hard refusals."
    )
    parser.add_argument("--data", type=Path, nargs="+", required=True)
    parser.add_argument("--out", type=Path, help="output JSONL (overwritten)")
    parser.add_argument(
        "--recheck-votes",
        type=int,
        default=DEFAULT_VERIFY_VOTES,
        help="teacher checks each hard refusal must pass again; 0 keeps them all",
    )
    parser.add_argument("--teacher", default="TEACHER")
    parser.add_argument("--temperature", type=float, default=0.7)
    parser.add_argument("--max-tokens", type=int, default=3072)
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true", help="use the fake teacher")
    args = parser.parse_args()

    if args.dry_run:
        teacher = FakeTeacher()
        out = args.out or Path(tempfile.mkdtemp(prefix="mix-dry-run-")) / "mixed.jsonl"
        args.workers = 1
    else:
        if not args.out:
            parser.error("--out is required unless --dry-run is given")
        out = args.out
        try:
            teacher = ChatClient(args.teacher)
        except EndpointError as error:
            parser.error(str(error))

    def recheck(messages: list[dict]) -> str:
        return teacher.chat(
            messages, temperature=args.temperature, max_tokens=args.max_tokens
        )

    rows = [row for path in args.data for row in read_jsonl(path)]
    golden = held_out_questions()
    try:
        kept, rejected = mix(rows, golden, recheck, args.recheck_votes, args.workers)
    except EndpointError as error:
        raise SystemExit(f"stopped: {error}") from error

    rejects = out.with_name(out.name + ".rejects.jsonl")
    for path in (out, rejects):
        path.unlink(missing_ok=True)
    for row in kept:
        append_jsonl(out, row)
    for row in rejected:
        append_jsonl(rejects, row)

    hard = sum(1 for row in kept if is_hard_refusal(row))
    print(f"read {len(rows)}, kept {len(kept)} in {out}")
    print(f"  rejected: {counts(rejected, 'reason')}")
    for field in ("lang", "kind", "distractors", "teacher"):
        print(f"  by {field}: {counts(kept, field)}")
    print(f"  hard refusals: {hard} ({hard / max(1, len(kept)):.1%})")
    if args.dry_run:
        print("DRY RUN: fake teacher, the rechecks are meaningless.")


if __name__ == "__main__":
    main()
