"""Ask a teacher model to write training examples from the OIA corpus.

    cd backend
    # no network, built-in fake teacher, writes to a temporary directory:
    uv run --frozen python ../ml/make_teacher_data.py --dry-run -n 40
    # real run (endpoint from TEACHER_BASE_URL / TEACHER_API_KEY / TEACHER_MODEL):
    uv run --frozen python ../ml/make_teacher_data.py -n 2000 --out ../ml/data/train.jsonl

Each example shows the student one passage that holds the answer plus one or
two distractor passages from other documents. For a refusal example the
answering passage is hidden from the student, so the right output is the fixed
refusal sentence with the OIA contact.

The teacher writes the question and, for answer examples, the bulleted answer.
An example is kept only if it passes `clean_example`; rejected ones go to
`<out>.rejects.jsonl` with the reason. Rerunning the same command resumes:
examples already written or rejected are skipped.

Several teachers: `--teachers TEACHER,TEACHER2` reads `TEACHER2_BASE_URL`,
`TEACHER2_API_KEY`, `TEACHER2_MODEL` for the second one; examples alternate
between them and every row records its `teacher`.
"""

import argparse
import json
import random
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path

from checks import (
    matches_golden,
    normalize_question,
    strip_reasoning,
    unsupported_numbers,
)
from common import (
    ChatClient,
    EndpointError,
    append_jsonl,
    load_golden,
    read_jsonl,
    student_services,
)
from prompt_format import (
    LANGS,
    OIA_EMAIL,
    build_messages,
    cited_numbers,
    format_passages,
    refusal_text,
)

DEFAULT_LANG_MIX = "en=0.55,zh-TW=0.15,vi=0.15,id=0.15"
DEFAULT_REFUSAL_RATE = 0.18
MIN_PASSAGE_CHARS = 200
MAX_QUESTION_CHARS = 300
LANG_NAMES = {
    "en": "English",
    "zh-TW": "Traditional Chinese as written in Taiwan",
    "vi": "Vietnamese",
    "id": "Indonesian",
}
_CJK = re.compile(r"[一-鿿]")
_BULLET = re.compile(r"^\s*[*•·]\s+", re.M)


def looks_chinese(text: str) -> bool:
    """Mostly Chinese characters; a quoted Chinese title in a Latin-script
    question does not count. Cannot tell en, vi and id apart."""
    letters = [char for char in text if char.isalpha()]
    return len(_CJK.findall(text)) > 0.3 * max(1, len(letters))


TEACHER_SYSTEM = (
    "You write training data for an assistant that answers administrative "
    "questions of international students at National Central University "
    "(Taiwan) from passages of the Office of International Affairs (OIA). "
    "Reply with one JSON object and nothing else."
)

ANSWER_TASK = """Passages the assistant will see:

{passages}

Write one question in {language} that a student would realistically ask and \
that passage [{gold}] answers. Then write the assistant's answer in {language}.

Requirements for the answer:
- 1 to 4 short bullet points, each starting with "- ".
- Every bullet ends with the number of the passage it is taken from, e.g. [{gold}].
- Use only facts written in the passages. Copy numbers, amounts, dates, URLs \
and names of forms, offices and systems exactly as written.
- Do not mention the passages or these instructions in the question.

If passage [{gold}] has no fact a student could ask about (menus, bare link \
lists, page footers), reply {{"skip": true}}.
Otherwise reply {{"question": "...", "answer": "- ... [{gold}]\\n- ... [{gold}]"}}"""

REFUSAL_TASK = """Passages the assistant will see:

{passages}

Hidden passage (the assistant will NOT see it):

{hidden}

Write one question in {language} that a student would realistically ask, that \
the hidden passage answers and that the passages the assistant will see do \
NOT answer. Do not mention the passages or these instructions in the question.

If the hidden passage has no fact a student could ask about, or every such \
fact is also in the visible passages, reply {{"skip": true}}.
Otherwise reply {{"question": "..."}}"""


@dataclass(frozen=True)
class Task:
    id: str
    kind: str  # "answer" or "refusal"
    lang: str
    teacher: int  # index into the list of teachers
    gold: dict  # the passage holding the answer
    shown: tuple[dict, ...]  # passages in the student's prompt, in order
    gold_number: int  # 1-based position of gold in shown; 0 for a refusal


def corpus_passages(services) -> list[dict]:
    """Every passage window of every document, cut as the backend's search does."""
    passages = []
    for document in services.load_documents():
        for start in range(0, max(1, len(document.lines)), services.PASSAGE_STEP):
            lines = document.lines[start : start + services.PASSAGE_LINES]
            passages.append(
                {
                    "id": document.id,
                    "title": document.title,
                    "start": start,
                    "passage": "\n".join(lines),
                }
            )
    return passages


def parse_lang_mix(text: str) -> dict[str, float]:
    mix = {}
    for part in text.split(","):
        lang, _, weight = part.partition("=")
        if lang.strip() not in LANGS:
            raise ValueError(f"unknown language {lang!r}; choose from {LANGS}")
        mix[lang.strip()] = float(weight)
    if not mix or sum(mix.values()) <= 0:
        raise ValueError("language mix needs at least one positive weight")
    return mix


def make_plan(
    passages: list[dict],
    count: int,
    seed: int,
    lang_mix: dict[str, float],
    refusal_rate: float,
    teachers: int,
    min_distractors: int = 1,
    max_distractors: int = 2,
) -> list[Task]:
    """`count` tasks. Task i depends only on the seed and i, not on `count`,
    so a rerun with a larger count resumes and then continues the same plan."""
    usable = [p for p in passages if len(p["passage"]) >= MIN_PASSAGE_CHARS]
    langs, weights = list(lang_mix), list(lang_mix.values())
    tasks = []
    for index in range(count):
        # Each pass over the corpus visits every usable passage once.
        cycle, position = divmod(index, len(usable))
        order = random.Random(f"{seed}-cycle-{cycle}").sample(usable, len(usable))
        gold = order[position]
        rng = random.Random(f"{seed}-task-{index}")
        kind = "refusal" if rng.random() < refusal_rate else "answer"
        lang = rng.choices(langs, weights)[0]
        others = [p for p in passages if p["id"] != gold["id"]]
        distractors = rng.sample(others, rng.randint(min_distractors, max_distractors))
        if kind == "refusal":
            # Keep two passages so a refusal prompt does not look different.
            while len(distractors) < 2:
                distractors.append(rng.choice(others))
            shown, gold_number = distractors, 0
        else:
            shown = [gold, *distractors]
            rng.shuffle(shown)
            gold_number = shown.index(gold) + 1
        tasks.append(
            Task(
                id=f"s{seed}-{index:05d}",
                kind=kind,
                lang=lang,
                teacher=index % teachers,
                gold=gold,
                shown=tuple(shown),
                gold_number=gold_number,
            )
        )
    return tasks


def teacher_messages(task: Task) -> list[dict]:
    values = {
        "passages": format_passages(list(task.shown)),
        "language": LANG_NAMES[task.lang],
        "gold": task.gold_number,
        "hidden": f"{task.gold['title']}\n{task.gold['passage']}",
    }
    template = ANSWER_TASK if task.kind == "answer" else REFUSAL_TASK
    return [
        {"role": "system", "content": TEACHER_SYSTEM},
        {"role": "user", "content": template.format(**values)},
    ]


def parse_json_object(text: str) -> dict | None:
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(text[start : end + 1])
    except ValueError:
        return None
    return value if isinstance(value, dict) else None


def clean_example(
    raw: str, task: Task, golden_normalized: list[str]
) -> tuple[dict | None, str]:
    """Turn a teacher reply into `{"question", "answer"}` or reject it.

    Returns (example, "ok") or (None, reason). Checks, in order: the reasoning
    channel is removed; the rest is a JSON object; the question is present,
    short, in the right script and not a golden question; for an answer
    example the answer is bulleted, cites only passages that exist including
    the one holding the answer, is not itself a refusal, and uses no number or
    month that the shown passages lack. A refusal example gets the fixed
    refusal sentence, never text written by the teacher.
    """
    data = parse_json_object(strip_reasoning(raw))
    if data is None:
        return None, "not_json"
    if data.get("skip"):
        return None, "teacher_skipped"
    question = data.get("question")
    if not isinstance(question, str) or not question.strip():
        return None, "no_question"
    question = " ".join(question.split())
    if len(question) > MAX_QUESTION_CHARS:
        return None, "question_too_long"
    if looks_chinese(question) != (task.lang == "zh-TW"):
        return None, "wrong_language"
    if matches_golden(question, golden_normalized):
        return None, "matches_golden_question"
    if task.kind == "refusal":
        return {"question": question, "answer": refusal_text(task.lang)}, "ok"

    answer = data.get("answer")
    if not isinstance(answer, str) or not answer.strip():
        return None, "no_answer"
    answer = _BULLET.sub("- ", answer.strip())
    lines = [line.strip() for line in answer.splitlines() if line.strip()]
    if not all(line.startswith("- ") for line in lines):
        return None, "not_bulleted"
    if OIA_EMAIL in answer.casefold():
        return None, "answer_is_refusal"
    cited = cited_numbers(answer)
    if not cited or not all(cited_numbers(line) for line in lines):
        return None, "bullet_without_citation"
    if any(not 1 <= number <= len(task.shown) for number in cited):
        return None, "citation_out_of_range"
    if task.gold_number not in cited:
        return None, "answer_passage_not_cited"
    source_text = "\n".join(p["title"] + "\n" + p["passage"] for p in task.shown)
    missing = unsupported_numbers(answer, source_text)
    if missing:
        return None, "number_not_in_passages: " + ", ".join(missing)
    return {"question": question, "answer": "\n".join(lines)}, "ok"


def to_row(task: Task, example: dict, teacher_name: str) -> dict:
    return {
        "id": task.id,
        "teacher": teacher_name,
        "lang": task.lang,
        "kind": task.kind,
        "source_id": task.gold["id"],
        "source_start": task.gold["start"],
        "shown": [f"{p['id']}@{p['start']}" for p in task.shown],
        "messages": build_messages(
            example["question"], list(task.shown), example["answer"]
        ),
    }


class FakeTeacher:
    """Offline stand-in for --dry-run. It only exercises the plumbing.

    It copies a line of the passage as the "answer"; every fifth reply is
    deliberately faulty (invented number, citation out of range, reply inside
    an unfinished thinking block) and every fourth carries a thinking block,
    so that the cleaning step visibly does something.
    """

    name = "fake-teacher"

    def __init__(self) -> None:
        self.calls = 0

    def chat(self, messages: list[dict], **_: object) -> str:
        self.calls += 1
        prompt = messages[-1]["content"]
        refusal = "Hidden passage" in prompt
        if refusal:
            body = prompt.split("Hidden passage", 1)[1].split("\n\n")[1]
            gold = 0
        else:
            gold = int(re.search(r"that passage \[(\d+)\] answers", prompt).group(1))
            body = prompt.split(f"\n[{gold}] ", 1)[1].split("\n\n")[0]
        lines = body.split("\n")
        fact = max(lines[1:] or lines, key=lambda line: sum(c.isdigit() for c in line))
        chinese = "Traditional Chinese" in prompt
        topic = lines[0][:60]
        question = (
            f"請問「{topic}」第 {self.calls} 題的規定是什麼？"
            if chinese
            else f"What does the OIA say about {topic} (dry run {self.calls})?"
        )
        reply: dict = {"question": question}
        if not refusal:
            reply["answer"] = f"* {fact.strip()} [{gold}]"
            if self.calls % 5 == 1:
                reply["answer"] += f"\n- The fee is NT$98,765 [{gold}]"
            elif self.calls % 5 == 2:
                reply["answer"] = f"- {fact.strip()} [9]"
        text = json.dumps(reply, ensure_ascii=False)
        if self.calls % 5 == 3:
            return "<think>Let me look at the passage" + text
        if self.calls % 4 == 0:
            return f"<think>The passage is about {topic}.</think>\n{text}"
        return text


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__.split("\n")[0],
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("-n", "--count", type=int, default=200, help="tasks to plan")
    parser.add_argument("--out", type=Path, help="output JSONL (appended to)")
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--lang-mix", default=DEFAULT_LANG_MIX)
    parser.add_argument("--refusal-rate", type=float, default=DEFAULT_REFUSAL_RATE)
    parser.add_argument("--min-distractors", type=int, default=1)
    parser.add_argument("--max-distractors", type=int, default=2)
    parser.add_argument(
        "--teachers",
        default="TEACHER",
        help="comma-separated environment prefixes, one per teacher endpoint",
    )
    parser.add_argument("--temperature", type=float, default=0.7)
    parser.add_argument("--max-tokens", type=int, default=1024)
    parser.add_argument("--dry-run", action="store_true", help="use the fake teacher")
    args = parser.parse_args()

    if args.dry_run:
        teachers: list = [FakeTeacher()]
        out = (
            args.out
            or Path(tempfile.mkdtemp(prefix="teacher-dry-run-")) / "train.jsonl"
        )
    else:
        if not args.out:
            parser.error("--out is required unless --dry-run is given")
        out = args.out
        try:
            teachers = [ChatClient(p.strip()) for p in args.teachers.split(",")]
        except EndpointError as error:
            parser.error(str(error))
    rejects = out.with_name(out.name + ".rejects.jsonl")

    services = student_services()
    golden = [normalize_question(item["question"]) for item in load_golden()]
    plan = make_plan(
        corpus_passages(services),
        args.count,
        args.seed,
        parse_lang_mix(args.lang_mix),
        args.refusal_rate,
        len(teachers),
        args.min_distractors,
        args.max_distractors,
    )
    done = {row["id"] for row in read_jsonl(out)} | {
        row["id"] for row in read_jsonl(rejects)
    }
    todo = [task for task in plan if task.id not in done]
    print(
        f"plan: {len(plan)} tasks, {len(plan) - len(todo)} already done, output {out}"
    )

    kept, reasons = 0, {}
    for number, task in enumerate(todo, start=1):
        teacher = teachers[task.teacher]
        try:
            raw = teacher.chat(
                teacher_messages(task),
                temperature=args.temperature,
                max_tokens=args.max_tokens,
            )
        except EndpointError as error:
            # Not recorded as done, so the next run tries this task again.
            print(f"stopping at {task.id}: {error}")
            break
        example, reason = clean_example(raw, task, golden)
        if example:
            append_jsonl(out, to_row(task, example, teacher.name))
            kept += 1
        else:
            append_jsonl(
                rejects,
                {"id": task.id, "teacher": teacher.name, "reason": reason, "raw": raw},
            )
            key = reason.split(":")[0]
            reasons[key] = reasons.get(key, 0) + 1
        if number % 25 == 0 or number == len(todo):
            print(f"  {number}/{len(todo)} asked, {kept} kept")

    rows = read_jsonl(out)
    print(f"this run: kept {kept}, rejected {sum(reasons.values())} {reasons}")
    print(f"file now has {len(rows)} examples:")
    for field in ("lang", "kind", "teacher"):
        counts: dict = {}
        for row in rows:
            counts[row[field]] = counts.get(row[field], 0) + 1
        print(f"  by {field}: {dict(sorted(counts.items()))}")
    if args.dry_run:
        print("DRY RUN: fake teacher, the examples are meaningless as training data.")


if __name__ == "__main__":
    main()
