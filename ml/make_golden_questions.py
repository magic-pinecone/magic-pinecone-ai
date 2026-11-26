"""Ask a teacher model to write a large exam set over the whole knowledge base.

    cd backend
    # no network, built-in fake teacher, writes to a temporary directory:
    uv run --frozen python ../ml/make_golden_questions.py --dry-run -n 40
    # real run (endpoint from TEACHER_BASE_URL / TEACHER_API_KEY / TEACHER_MODEL):
    uv run --frozen python ../ml/make_golden_questions.py -n 1500

Rows have the format of `golden/questions.jsonl` plus `generated: true` and
are appended to `golden/generated.jsonl`; score them with `--golden` of
eval_generation.py and eval_retrieval.py. Training data never contains them
(`common.held_out_questions`).

An answerable question is written from one passage, with 1 to 3 strings
(`must_contain`) copied from it. It is kept only if the question is in the
right language, every string is found in the passage, the question is not
close to a golden or earlier generated question, and the teacher, shown only
that passage and the question, answers with all the strings. An unanswerable
question is kept only if the teacher says `--verify-votes` times that the five
passages the search returns for it do not answer it. Rejected tasks go to
`<out>.rejects.jsonl` with the reason. Rerunning the same command resumes.
"""

import argparse
import json
import re
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from checks import holds_answer, matches_golden, normalize_question, strip_reasoning
from common import (
    GENERATED_PATH,
    ChatClient,
    EndpointError,
    append_jsonl,
    held_out_questions,
    knowledge_services,
    read_jsonl,
)
from make_teacher_data import (
    DEFAULT_VERIFY_VOTES,
    LANG_NAMES,
    MAX_QUESTION_CHARS,
    FakeTeacher,
    Task,
    corpus_passages,
    looks_chinese,
    make_plan,
    parse_json_object,
    parse_lang_mix,
    parse_type_mix,
    refusal_confirmed_by_votes,
)
from prompt_format import format_passages

DEFAULT_LANG_MIX = "en=0.4,zh-TW=0.2,vi=0.2,id=0.2"
DEFAULT_UNANSWERABLE_RATE = 0.15
DEFAULT_TYPE_MIX = (
    "course=0.12,food_place=0.12,academic_calendar=0.12,oia_page=0.12,"
    "gov_guide=0.08,graduation_language=0.06,intl_program=0.06,credits=0.06,"
    "service_learning=0.05,contact=0.04,place=0.06,destination=0.05,club=0.06"
)
ID_LANG = {"en": "en", "zh-TW": "zh", "vi": "vi", "id": "id"}
NEEDS_NATIVE_REVIEW = {"vi", "id"}
MAX_MUST_CONTAIN = 3
MAX_FACT_CHARS = 80
SEARCH_LIMIT = 5
TOPICS = {
    "oia_page": "pages of the Office of International Affairs",
    "gov_guide": "government guides for international students",
    "graduation_language": "language requirements for graduation",
    "intl_program": "international exchange and degree programs",
    "credits": "credit and course-selection rules",
    "service_learning": "service-learning requirements",
    "contact": "offices and contact details",
    "place": "places on the campus",
    "destination": "exchange destinations abroad",
    "food_place": "places to eat around the campus",
    "academic_calendar": "the academic calendar and its dates",
    "course": "courses offered by the university",
    "club": "student clubs",
}

TEACHER_SYSTEM = (
    "You write exam questions for an assistant that answers questions of "
    "students at National Central University (NCU, Taiwan) from a knowledge "
    "base. Reply with one JSON object and nothing else."
)

QUESTION_TASK = """Passage:

{passage}

Write one question in {language} that a student of NCU would naturally ask \
and that this passage alone answers. Do not mention the passage. The \
question must stand on its own: name the course (title or code), place, \
office, program, event or document it is about, never "this course" or "this \
page".

Also list 1 to 3 short strings (`must_contain`) that a correct answer has to \
contain. Copy each one exactly, character for character, from the passage. \
Prefer numbers, dates, times, codes, room and building names, URLs and proper \
names, because they stay the same in every language. Do not list strings \
that the question itself already contains.

If the passage has no fact a student could ask about (menus, bare link lists, \
page footers), reply {{"skip": true}}.
Otherwise reply {{"question": "...", "must_contain": ["..."]}}"""

ANSWER_TASK = """Passage:

{passage}

Question: {question}

Answer the question in {language} using only the passage, in one or two \
short sentences. Copy numbers, dates, times, codes and names exactly as the \
passage writes them. If the passage does not answer it, say so."""

UNANSWERABLE_TASK = """Topic of the knowledge base: {topic}.

A passage of it, as inspiration:

{passage}

Write one question in {language} that a student of NCU could plausibly ask \
about this topic but that neither this passage nor the rest of the knowledge \
base answers, for example a detail that is not published. Do not mention the \
passage.
Reply {{"question": "..."}}"""


def make_exam_plan(
    passages: list[dict],
    count: int,
    seed: int,
    lang_mix: dict[str, float],
    unanswerable_rate: float,
    type_mix: dict[str, float],
) -> list[Task]:
    return make_plan(
        passages,
        count,
        seed,
        lang_mix,
        unanswerable_rate,
        1,
        type_mix=type_mix,
    )


def task_number(task: Task) -> int:
    return int(task.id.rsplit("-", 1)[1])


def exam_id(task: Task) -> str:
    return f"gen-{ID_LANG[task.lang]}-{task_number(task):04d}"


def passage_text(passage: dict) -> str:
    return f"{passage['title']}\n{passage['passage']}"


def passage_id(passage: dict) -> str:
    return f"{passage['id']}@{passage['start']}"


def question_messages(task: Task) -> list[dict]:
    values = {
        "passage": passage_text(task.gold),
        "language": LANG_NAMES[task.lang],
        "topic": TOPICS.get(task.gold.get("source_type"), "the university"),
    }
    template = UNANSWERABLE_TASK if task.kind == "refusal" else QUESTION_TASK
    return [
        {"role": "system", "content": TEACHER_SYSTEM},
        {"role": "user", "content": template.format(**values)},
    ]


def answer_messages(task: Task, question: str) -> list[dict]:
    content = ANSWER_TASK.format(
        passage=passage_text(task.gold),
        question=question,
        language=LANG_NAMES[task.lang],
    )
    return [
        {"role": "system", "content": TEACHER_SYSTEM},
        {"role": "user", "content": content},
    ]


def read_question(raw: str, task: Task, blocked: list[str]) -> tuple[dict | None, str]:
    data = parse_json_object(strip_reasoning(raw))
    if data is None:
        return None, "not_json"
    if data.get("skip"):
        return None, "teacher_skipped"
    question = data.get("question")
    if not isinstance(question, str) or not question.strip():
        return None, "no_question"
    data["question"] = " ".join(question.split())
    if len(data["question"]) > MAX_QUESTION_CHARS:
        return None, "question_too_long"
    if looks_chinese(data["question"]) != (task.lang == "zh-TW"):
        return None, "wrong_language"
    if matches_golden(data["question"], blocked):
        return None, "matches_golden_question"
    return data, "ok"


def clean_facts(value: object) -> list[str] | None:
    if not isinstance(value, list) or not 1 <= len(value) <= MAX_MUST_CONTAIN:
        return None
    if not all(isinstance(fact, str) and fact.strip() for fact in value):
        return None
    facts = [fact.strip() for fact in value]
    if any(len(fact) > MAX_FACT_CHARS for fact in facts):
        return None
    return list(dict.fromkeys(facts))


def accept_answerable(raw: str, task: Task, blocked: list[str], chat) -> tuple:
    data, reason = read_question(raw, task, blocked)
    if data is None:
        return None, reason
    facts = clean_facts(data.get("must_contain"))
    if facts is None:
        return None, "bad_must_contain"
    item = {"must_contain": facts}
    if not holds_answer(passage_text(task.gold), item):
        return None, "must_contain_not_in_passage"
    facts = [
        f for f in facts if not holds_answer(data["question"], {"must_contain": [f]})
    ]
    if not facts:
        return None, "answer_in_question"
    item = {"must_contain": facts}
    verdict = strip_reasoning(chat(answer_messages(task, data["question"])))
    if not holds_answer(verdict, item):
        return None, "verification_answer_lacks_facts"
    return {
        "question": data["question"],
        "answerable": True,
        "source_id": task.gold["id"],
        "must_contain": facts,
        "source_type": task.gold.get("source_type"),
        "passage_id": passage_id(task.gold),
    }, "ok"


def accept_unanswerable(
    raw: str, task: Task, blocked: list[str], chat, search, votes: int
) -> tuple:
    data, reason = read_question(raw, task, blocked)
    if data is None:
        return None, reason
    found = search(data["question"], limit=SEARCH_LIMIT)
    shown = [{"title": r["title"], "passage": r["passage"]} for r in found]
    confirmed, reason = refusal_confirmed_by_votes(
        chat, format_passages(shown), data["question"], votes
    )
    if not confirmed:
        return None, reason
    return {
        "question": data["question"],
        "answerable": False,
        "source_id": None,
        "must_contain": [],
        "source_type": "none",
    }, "ok"


def to_row(task: Task, fields: dict) -> dict:
    row = {
        "id": exam_id(task),
        "lang": task.lang,
        "question": fields["question"],
        "answerable": fields["answerable"],
        "source_id": fields["source_id"],
        "must_contain": fields["must_contain"],
        "needs_native_review": task.lang in NEEDS_NATIVE_REVIEW,
        "source_type": fields["source_type"],
        "generated": True,
    }
    if "passage_id" in fields:
        row["passage_id"] = fields["passage_id"]
    return row


class FakeExamTeacher(FakeTeacher):
    def chat(self, messages: list[dict], **kwargs: object) -> str:
        prompt = messages[-1]["content"]
        if not prompt.startswith(("Passage:", "Topic of the knowledge base")):
            return super().chat(messages, **kwargs)
        self.calls += 1
        chinese = "Traditional Chinese" in prompt
        if prompt.startswith("Topic of the knowledge base"):
            body = prompt.split("inspiration:\n\n", 1)[1].split("\n\nWrite one", 1)[0]
            topic = body.split("\n")[0][:40]
            return json.dumps(
                {
                    "question": f"請問「{topic}」有沒有第 {self.calls} 項補充規定？"
                    if chinese
                    else f"Is there an extra rule {self.calls} about {topic}?"
                },
                ensure_ascii=False,
            )
        body = prompt.split("Passage:\n\n", 1)[1].split("\n\n", 1)[0]
        if "\nQuestion: " in prompt:
            return "I do not know." if self.calls % 6 == 0 else body
        topic = body.split("\n")[0][:40]
        found = re.findall(r"\d[\d,.:/-]*\d|\d", body)
        if not found:
            return json.dumps({"skip": True})
        fact = "ZZ-404" if self.calls % 7 == 0 else found[0]
        question = (
            f"請問「{topic}」第 {self.calls} 題的規定是什麼？"
            if chinese
            else f"What does the page say about {topic} (item {self.calls})?"
        )
        return json.dumps(
            {"question": question, "must_contain": [fact]}, ensure_ascii=False
        )


def count_by(rows: list[dict], field: str) -> dict:
    counts: dict = {}
    for row in rows:
        counts[row.get(field)] = counts.get(row.get(field), 0) + 1
    return dict(sorted(counts.items(), key=lambda item: str(item[0])))


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__.split("\n")[0],
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("-n", "--count", type=int, default=200, help="tasks to plan")
    parser.add_argument(
        "--out", type=Path, default=GENERATED_PATH, help="output JSONL (appended to)"
    )
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--lang-mix", default=DEFAULT_LANG_MIX)
    parser.add_argument("--type-mix", default=DEFAULT_TYPE_MIX)
    parser.add_argument(
        "--unanswerable-rate", type=float, default=DEFAULT_UNANSWERABLE_RATE
    )
    parser.add_argument("--teacher", default="TEACHER", help="environment prefix")
    parser.add_argument("--temperature", type=float, default=0.7)
    parser.add_argument("--max-tokens", type=int, default=3072)
    parser.add_argument(
        "--verify-votes",
        type=int,
        default=DEFAULT_VERIFY_VOTES,
        help="teacher checks an unanswerable question must pass, each saying "
        "the search results do not answer it",
    )
    parser.add_argument("--search", choices=("keyword", "hybrid"), default="hybrid")
    parser.add_argument("--workers", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true", help="use the fake teacher")
    args = parser.parse_args()

    if args.dry_run:
        teacher = FakeExamTeacher()
        out = (
            args.out
            if args.out != GENERATED_PATH
            else Path(tempfile.mkdtemp(prefix="golden-dry-run-")) / "generated.jsonl"
        )
        args.search = "keyword"
        args.workers = 1
    else:
        out = args.out
        try:
            teacher = ChatClient(args.teacher)
        except EndpointError as error:
            parser.error(str(error))
    rejects = out.with_name(out.name + ".rejects.jsonl")

    services = knowledge_services(args.search)
    passages = corpus_passages(services)
    try:
        type_mix = parse_type_mix(
            args.type_mix,
            {p.get("source_type", "oia_page") for p in passages},
            min_primary_share=0.0,
        )
        lang_mix = parse_lang_mix(args.lang_mix)
    except ValueError as error:
        parser.error(str(error))
    plan = make_exam_plan(
        passages, args.count, args.seed, lang_mix, args.unanswerable_rate, type_mix
    )
    done = {row["id"] for row in read_jsonl(out)} | {
        row["id"] for row in read_jsonl(rejects)
    }
    todo = [task for task in plan if exam_id(task) not in done]
    print(
        f"plan: {len(plan)} tasks, {len(plan) - len(todo)} already done, output {out}"
    )

    blocked = list(
        dict.fromkeys(
            held_out_questions()
            + [normalize_question(row["question"]) for row in read_jsonl(out)]
        )
    )

    def chat(messages: list[dict]) -> str:
        return teacher.chat(
            messages, temperature=args.temperature, max_tokens=args.max_tokens
        )

    def produce(task: Task) -> tuple[dict | None, str, str] | EndpointError:
        try:
            raw = chat(question_messages(task))
            if task.kind == "refusal":
                fields, reason = accept_unanswerable(
                    raw, task, blocked, chat, services.search, args.verify_votes
                )
            else:
                fields, reason = accept_answerable(raw, task, blocked, chat)
        except EndpointError as error:
            return error
        return fields, reason, raw

    kept, reasons, number, stopped = 0, {}, 0, False
    batch = max(1, args.workers) * 4
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        for start in range(0, len(todo), batch):
            chunk = todo[start : start + batch]
            for task, result in zip(chunk, pool.map(produce, chunk), strict=True):
                number += 1
                if isinstance(result, EndpointError):
                    print(f"stopping at {exam_id(task)}: {result}")
                    stopped = True
                    break
                fields, reason, raw = result
                if fields and matches_golden(fields["question"], blocked):
                    fields, reason = None, "duplicate_generated_question"
                if fields:
                    append_jsonl(out, to_row(task, fields))
                    blocked.append(normalize_question(fields["question"]))
                    kept += 1
                else:
                    append_jsonl(
                        rejects, {"id": exam_id(task), "reason": reason, "raw": raw}
                    )
                    key = reason.split(":")[0]
                    reasons[key] = reasons.get(key, 0) + 1
                if number % 25 == 0 or number == len(todo):
                    print(f"  {number}/{len(todo)} asked, {kept} kept", flush=True)
            if stopped:
                break

    rows = read_jsonl(out)
    print(f"this run: kept {kept}, rejected {sum(reasons.values())} {reasons}")
    print(f"file now has {len(rows)} questions:")
    for field in ("lang", "source_type", "answerable"):
        print(f"  by {field}: {count_by(rows, field)}")
    if args.dry_run:
        print("DRY RUN: fake teacher, the questions are meaningless.")


if __name__ == "__main__":
    main()
