"""Ask a teacher model to write training examples from the OIA corpus.

    cd backend
    # no network, built-in fake teacher, writes to a temporary directory:
    uv run --frozen python ../ml/make_teacher_data.py --dry-run -n 40
    # real run (endpoint from TEACHER_BASE_URL / TEACHER_API_KEY / TEACHER_MODEL):
    uv run --frozen python ../ml/make_teacher_data.py -n 2000 --out ../ml/data/train.jsonl

Each example shows the student one passage that holds the answer plus one or
more distractor passages from other documents. For a refusal example the
answering passage is hidden from the student, so the right output is the fixed
refusal sentence with the OIA contact. A hard refusal (`--hard-refusal-rate`)
shows instead what the search returns for the question without the answering
document, and is kept only when the teacher confirms that those passages do
not answer it: the student learns to refuse when related passages lack the
answer.

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
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
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
    knowledge_services,
    load_golden,
    read_jsonl,
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
DEFAULT_MULTI_RATE = 0.10
DEFAULT_HARD_RATE = 0.70
DEFAULT_HARD_REFUSAL_RATE = 0.60
DEFAULT_TYPE_MIX = (
    "oia_page=0.40,gov_guide=0.14,graduation_language=0.07,intl_program=0.07,"
    "credits=0.08,service_learning=0.04,contact=0.03,place=0.06,destination=0.04,"
    "course=0.04,club=0.03"
)
SOURCE_TYPES = (
    "oia_page",
    "gov_guide",
    "graduation_language",
    "intl_program",
    "credits",
    "service_learning",
    "contact",
    "place",
    "destination",
    "course",
    "club",
)
PRIMARY_TYPES = ("oia_page", "gov_guide")
MIN_PRIMARY_SHARE = 0.5
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
_QUOTED = re.compile(r"\([^)]*\)|（[^）]*）|「[^」]*」|https?://\S+")


def looks_chinese(text: str) -> bool:
    """Mostly Chinese characters; a quoted Chinese title in a Latin-script
    question does not count. Cannot tell en, vi and id apart."""
    letters = [char for char in text if char.isalpha()]
    return len(_CJK.findall(text)) > 0.3 * max(1, len(letters))


def in_language(answer: str, lang: str) -> bool:
    """Is the answer written in its language? Text in parentheses or 「」 and
    URLs are left out, so a Chinese name may follow its translation."""
    text = _QUOTED.sub(" ", answer)
    letters = [char for char in text if char.isalpha()]
    share = len(_CJK.findall(text)) / max(1, len(letters))
    return share > 0.3 if lang == "zh-TW" else share <= 0.1


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
- Write the answer only in {language}. Translate facts taken from \
passages in another language; keep numbers, URLs and official names as \
written (an original Chinese name may follow its translation in parentheses).
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

MULTI_TASK = """Passages the assistant will see:

{passages}

Write one question in {language} that a student would realistically ask and \
that can only be answered with facts from both passage [{gold}] and passage \
[{second}]. Then write the assistant's answer in {language}.

Requirements for the answer:
- 2 to 4 short bullet points, each starting with "- ".
- Write the answer only in {language}. Translate facts taken from \
passages in another language; keep numbers, URLs and official names as \
written (an original Chinese name may follow its translation in parentheses).
- Every bullet ends with the number of the passage it is taken from; both \
[{gold}] and [{second}] must be cited at least once.
- Use only facts written in the passages. Copy numbers, amounts, dates, URLs \
and names of forms, offices and systems exactly as written.
- Do not mention the passages or these instructions in the question.

If the two passages share no subject a student could ask about together, \
reply {{"skip": true}}.
Otherwise reply {{"question": "...", "answer": "- ... [{gold}]\\n- ... [{second}]"}}"""


VERIFY_TASK = """Passages:

{passages}

Question: {question}

Do these passages contain the answer to the question, fully or in part? \
Reply {{"answered": true}} or {{"answered": false}}."""


@dataclass(frozen=True)
class Task:
    id: str
    kind: str  # "answer", "multi" or "refusal"
    lang: str
    teacher: int  # index into the list of teachers
    gold: dict  # the passage holding the answer
    shown: tuple[dict, ...]  # passages in the student's prompt, in order
    gold_number: int  # 1-based position of gold in shown; 0 for a refusal
    second: dict | None = None  # second answering passage of a "multi" task
    second_number: int = 0
    hard: bool = False  # swap the distractors for search results afterwards


def corpus_passages(services) -> list[dict]:
    """Every passage window of every document, cut as the backend's search does."""
    passages = []
    for document in services.load_documents():
        for start in range(0, max(1, len(document.lines)), services.PASSAGE_STEP):
            lines = document.lines[start : start + services.PASSAGE_LINES]
            passages.append(
                {
                    "id": document.id,
                    "source_type": document.source_type,
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


def parse_type_mix(text: str, available: set[str] | None = None) -> dict[str, float]:
    mix = {}
    for part in text.split(","):
        source_type, _, weight = part.partition("=")
        mix[source_type.strip()] = float(weight)
    if available is not None:
        unknown = set(mix) - set(SOURCE_TYPES)
        if unknown:
            raise ValueError(f"unknown source types {sorted(unknown)}")
        mix = {k: v for k, v in mix.items() if k in available and v > 0}
    total = sum(mix.values())
    if total <= 0:
        raise ValueError("source-type mix needs at least one positive weight")
    if sum(mix.get(k, 0.0) for k in PRIMARY_TYPES) / total < MIN_PRIMARY_SHARE:
        raise ValueError(
            f"{' and '.join(PRIMARY_TYPES)} need at least "
            f"{MIN_PRIMARY_SHARE:.0%} of the examples"
        )
    return mix


def lines_overlap(first: dict, second: dict) -> bool:
    shared = set(first["passage"].split("\n")) & set(second["passage"].split("\n"))
    if not shared:
        return False
    if first["id"] == second["id"]:
        return True
    smaller = min(len(first["passage"].split("\n")), len(second["passage"].split("\n")))
    return len(shared) * 2 >= smaller


def pick_second(gold: dict, pools: dict, rng: random.Random, neighbor) -> dict:
    found = neighbor(gold) if neighbor else None
    if found and not lines_overlap(found, gold):
        return found
    same_document = [
        p
        for p in pools[gold["source_type"]]
        if p["id"] == gold["id"] and not lines_overlap(p, gold)
    ]
    if same_document:
        return rng.choice(same_document)
    return rng.choice(
        [p for p in pools[gold["source_type"]] if p["id"] != gold["id"]]
        or [p for p in pools[gold["source_type"]] if not lines_overlap(p, gold)]
    )


def make_plan(
    passages: list[dict],
    count: int,
    seed: int,
    lang_mix: dict[str, float],
    refusal_rate: float,
    teachers: int,
    min_distractors: int = 1,
    max_distractors: int = 2,
    type_mix: dict[str, float] | None = None,
    multi_rate: float = 0.0,
    hard_rate: float = 0.0,
    neighbor=None,
    hard_refusal_rate: float = 0.0,
) -> list[Task]:
    """`count` tasks. Task i depends only on the seed and i, not on `count`,
    so a rerun with a larger count resumes and then continues the same plan."""
    usable = [p for p in passages if len(p["passage"]) >= MIN_PASSAGE_CHARS]
    pools: dict[str, list[dict]] = {}
    for passage in usable:
        pools.setdefault(passage.get("source_type", "oia_page"), []).append(passage)
    if type_mix is None:
        type_mix = {name: 1.0 for name in pools}
    type_mix = {k: v for k, v in type_mix.items() if k in pools and v > 0}
    types, type_weights = list(type_mix), list(type_mix.values())
    langs, weights = list(lang_mix), list(lang_mix.values())
    visits = dict.fromkeys(types, 0)
    tasks = []
    for index in range(count):
        rng = random.Random(f"{seed}-task-{index}")
        source_type = rng.choices(types, type_weights)[0]
        pool = pools[source_type]
        cycle, position = divmod(visits[source_type], len(pool))
        visits[source_type] += 1
        order = random.Random(f"{seed}-{source_type}-cycle-{cycle}").sample(
            pool, len(pool)
        )
        gold = order[position]
        draw = rng.random()
        if draw < refusal_rate:
            kind = "refusal"
        elif draw < refusal_rate + multi_rate:
            kind = "multi"
        else:
            kind = "answer"
        lang = rng.choices(langs, weights)[0]
        rate = {"answer": hard_rate, "refusal": hard_refusal_rate}.get(kind, 0.0)
        hard = rate > 0 and rng.random() < rate
        others = [p for p in passages if p["id"] != gold["id"]]
        second, second_number = None, 0
        if kind == "multi":
            second = pick_second(gold, pools, rng, neighbor)
            others = [p for p in others if p["id"] != second["id"]]
            distractors = rng.sample(others, rng.randint(0, 1))
            shown = [gold, second, *distractors]
            rng.shuffle(shown)
            gold_number = shown.index(gold) + 1
            second_number = shown.index(second) + 1
        else:
            distractors = rng.sample(
                others, rng.randint(min_distractors, max_distractors)
            )
            if kind == "refusal":
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
                second=second,
                second_number=second_number,
                hard=hard,
            )
        )
    return tasks


def hard_distractors(task: Task, question: str, search, starts: dict) -> tuple:
    answering = [task.gold] + ([task.second] if task.second else [])
    wanted = sum(p not in answering for p in task.shown)
    found = []
    for result in search(question, limit=wanted + 6):
        key = (result["id"], result["passage"])
        candidate = {
            "id": result["id"],
            "source_type": result.get("source_type"),
            "title": result["title"],
            "start": starts.get(key, -1),
            "passage": result["passage"],
        }
        if task.kind == "refusal" and candidate["id"] == task.gold["id"]:
            continue
        if not any(lines_overlap(candidate, p) for p in answering):
            found.append(candidate)
    replacements = iter(found[:wanted])
    kept = iter([p for p in task.shown if p not in answering])
    return tuple(
        p if p in answering else next(replacements, None) or next(kept)
        for p in task.shown
    )


def teacher_messages(task: Task) -> list[dict]:
    values = {
        "passages": format_passages(list(task.shown)),
        "language": LANG_NAMES[task.lang],
        "gold": task.gold_number,
        "second": task.second_number,
        "hidden": f"{task.gold['title']}\n{task.gold['passage']}",
    }
    template = {
        "answer": ANSWER_TASK,
        "multi": MULTI_TASK,
        "refusal": REFUSAL_TASK,
    }[task.kind]
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
    if not in_language(answer, task.lang):
        return None, "answer_not_in_language"
    cited = cited_numbers(answer)
    if not cited or not all(cited_numbers(line) for line in lines):
        return None, "bullet_without_citation"
    if any(not 1 <= number <= len(task.shown) for number in cited):
        return None, "citation_out_of_range"
    if task.gold_number not in cited:
        return None, "answer_passage_not_cited"
    if task.kind == "multi" and task.second_number not in cited:
        return None, "second_passage_not_cited"
    source_text = "\n".join(p["title"] + "\n" + p["passage"] for p in task.shown)
    missing = unsupported_numbers(answer, source_text)
    if missing:
        return None, "number_not_in_passages: " + ", ".join(missing)
    return {"question": question, "answer": "\n".join(lines)}, "ok"


def swap_distractors(
    task: Task, example: dict, search, starts: dict
) -> tuple[Task, str]:
    shown = hard_distractors(task, example["question"], search, starts)
    swapped = replace(task, shown=shown)
    if task.kind == "refusal":
        return swapped, "ok"
    source_text = "\n".join(p["title"] + "\n" + p["passage"] for p in shown)
    missing = unsupported_numbers(example["answer"], source_text)
    if missing:
        return task, "number_not_in_passages_after_swap: " + ", ".join(missing)
    return swapped, "ok"


def verify_messages(task: Task, question: str) -> list[dict]:
    content = VERIFY_TASK.format(
        passages=format_passages(list(task.shown)), question=question
    )
    return [
        {"role": "system", "content": TEACHER_SYSTEM},
        {"role": "user", "content": content},
    ]


def refusal_confirmed(raw: str) -> tuple[bool, str]:
    data = parse_json_object(strip_reasoning(raw))
    if data is None or not isinstance(data.get("answered"), bool):
        return False, "verify_not_json"
    if data["answered"]:
        return False, "visible_passages_answer"
    return True, "ok"


def to_row(task: Task, example: dict, teacher_name: str) -> dict:
    return {
        "id": task.id,
        "teacher": teacher_name,
        "lang": task.lang,
        "kind": task.kind,
        "source_id": task.gold["id"],
        "source_type": task.gold.get("source_type"),
        "second_source_id": task.second["id"] if task.second else None,
        "distractors": "retrieved" if task.hard else "random",
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
        if "Do these passages contain the answer" in prompt:
            return json.dumps({"answered": self.calls % 3 == 0})
        refusal = "Hidden passage" in prompt
        pair = re.search(
            r"facts from both passage \[(\d+)\] and passage \[(\d+)\]", prompt
        )
        if pair:
            first, second = int(pair.group(1)), int(pair.group(2))
            facts = []
            for number in (first, second):
                body = prompt.split(f"\n[{number}] ", 1)[1].split("\n\n")[0]
                lines = body.split("\n")
                facts.append(
                    max(lines[1:] or lines, key=lambda x: sum(c.isdigit() for c in x))
                )
            chinese = "Traditional Chinese" in prompt
            question = (
                f"請問兩段資料合起來有什麼規定（試跑 {self.calls}）？"
                if chinese
                else f"What do both pages say together (dry run {self.calls})?"
            )
            reply = {
                "question": question,
                "answer": f"- {facts[0].strip()} [{first}]\n- {facts[1].strip()} [{second}]",
            }
            return json.dumps(reply, ensure_ascii=False)
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
    parser.add_argument("--min-distractors", type=int, default=2)
    parser.add_argument("--max-distractors", type=int, default=4)
    parser.add_argument(
        "--teachers",
        default="TEACHER",
        help="comma-separated environment prefixes, one per teacher endpoint",
    )
    parser.add_argument("--temperature", type=float, default=0.7)
    parser.add_argument("--max-tokens", type=int, default=3072)
    parser.add_argument("--type-mix", default=DEFAULT_TYPE_MIX)
    parser.add_argument("--multi-rate", type=float, default=DEFAULT_MULTI_RATE)
    parser.add_argument("--hard-rate", type=float, default=DEFAULT_HARD_RATE)
    parser.add_argument(
        "--hard-refusal-rate", type=float, default=DEFAULT_HARD_REFUSAL_RATE
    )
    parser.add_argument("--search", choices=("keyword", "hybrid"), default="hybrid")
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true", help="use the fake teacher")
    args = parser.parse_args()

    if args.dry_run:
        teachers: list = [FakeTeacher()]
        out = (
            args.out
            or Path(tempfile.mkdtemp(prefix="teacher-dry-run-")) / "train.jsonl"
        )
        args.search = "keyword"
        args.workers = 1
    else:
        if not args.out:
            parser.error("--out is required unless --dry-run is given")
        out = args.out
        try:
            teachers = [ChatClient(p.strip()) for p in args.teachers.split(",")]
        except EndpointError as error:
            parser.error(str(error))
    rejects = out.with_name(out.name + ".rejects.jsonl")

    services = knowledge_services(args.search)
    golden = [normalize_question(item["question"]) for item in load_golden()]
    passages = corpus_passages(services)
    starts = {(p["id"], p["passage"]): p["start"] for p in passages}

    def neighbor(gold: dict) -> dict | None:
        query = gold["title"] + "\n" + gold["passage"][:300]
        for result in services.search(query, limit=6):
            candidate = {
                "id": result["id"],
                "source_type": result.get("source_type"),
                "title": result["title"],
                "start": starts.get((result["id"], result["passage"]), -1),
                "passage": result["passage"],
            }
            if len(candidate["passage"]) >= MIN_PASSAGE_CHARS and not lines_overlap(
                candidate, gold
            ):
                return candidate
        return None

    try:
        type_mix = parse_type_mix(
            args.type_mix, {p.get("source_type", "oia_page") for p in passages}
        )
    except ValueError as error:
        parser.error(str(error))
    plan = make_plan(
        passages,
        args.count,
        args.seed,
        parse_lang_mix(args.lang_mix),
        args.refusal_rate,
        len(teachers),
        args.min_distractors,
        args.max_distractors,
        type_mix,
        args.multi_rate,
        args.hard_rate,
        neighbor,
        args.hard_refusal_rate,
    )
    done = {row["id"] for row in read_jsonl(out)} | {
        row["id"] for row in read_jsonl(rejects)
    }
    todo = [task for task in plan if task.id not in done]
    print(
        f"plan: {len(plan)} tasks, {len(plan) - len(todo)} already done, output {out}"
    )

    def chat(task: Task, messages: list[dict]) -> str:
        return teachers[task.teacher].chat(
            messages, temperature=args.temperature, max_tokens=args.max_tokens
        )

    def produce(task: Task) -> tuple[Task, dict | None, str, str] | EndpointError:
        try:
            raw = chat(task, teacher_messages(task))
            example, reason = clean_example(raw, task, golden)
            if example and task.hard:
                task, reason = swap_distractors(task, example, services.search, starts)
                example = example if reason == "ok" else None
            if example and task.hard and task.kind == "refusal":
                verdict = chat(task, verify_messages(task, example["question"]))
                confirmed, reason = refusal_confirmed(verdict)
                example = example if confirmed else None
        except EndpointError as error:
            return error
        return task, example, reason, raw

    kept, reasons, number, stopped = 0, {}, 0, False
    batch = max(1, args.workers) * 4
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        for start in range(0, len(todo), batch):
            chunk = todo[start : start + batch]
            for planned, result in zip(chunk, pool.map(produce, chunk), strict=True):
                number += 1
                teacher = teachers[planned.teacher]
                if isinstance(result, EndpointError):
                    print(f"stopping at {planned.id}: {result}")
                    stopped = True
                    break
                task, example, reason, raw = result
                if example:
                    append_jsonl(out, to_row(task, example, teacher.name))
                    kept += 1
                else:
                    append_jsonl(
                        rejects,
                        {
                            "id": task.id,
                            "teacher": teacher.name,
                            "reason": reason,
                            "raw": raw,
                        },
                    )
                    key = reason.split(":")[0]
                    reasons[key] = reasons.get(key, 0) + 1
                if number % 25 == 0 or number == len(todo):
                    print(f"  {number}/{len(todo)} asked, {kept} kept", flush=True)
            if stopped:
                break

    rows = read_jsonl(out)
    print(f"this run: kept {kept}, rejected {sum(reasons.values())} {reasons}")
    print(f"file now has {len(rows)} examples:")
    for field in ("lang", "kind", "source_type", "distractors", "teacher"):
        counts: dict = {}
        for row in rows:
            counts[row.get(field)] = counts.get(row.get(field), 0) + 1
        print(f"  by {field}: {dict(sorted(counts.items()))}")
    if args.dry_run:
        print("DRY RUN: fake teacher, the examples are meaningless as training data.")


if __name__ == "__main__":
    main()
