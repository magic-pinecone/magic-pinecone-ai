import json

from checks import normalize_question
from common import held_out_questions
from make_golden_questions import (
    DEFAULT_TYPE_MIX,
    accept_answerable,
    accept_unanswerable,
    exam_id,
    make_exam_plan,
    to_row,
)
from make_teacher_data import Task, parse_lang_mix, parse_type_mix

PASSAGE = {
    "id": "oia-exam",
    "source_type": "academic_calendar",
    "title": "Exam week",
    "start": 8,
    "passage": "Midterm exams are held in room E1-201 from 2026-04-20 to 2026-04-24.",
}
PASSAGES = [
    {**PASSAGE, "id": f"doc-{i}", "source_type": t, "passage": PASSAGE["passage"] * 4}
    for i, t in enumerate(["course", "food_place", "oia_page", "academic_calendar"] * 5)
]


def task(kind="answer", lang="en", gold=PASSAGE):
    return Task("s0-00042", kind, lang, 0, gold, (gold,), 1)


def reply(question, facts):
    return json.dumps({"question": question, "must_contain": facts})


def plan(count, **kwargs):
    mix = parse_type_mix(DEFAULT_TYPE_MIX, {p["source_type"] for p in PASSAGES}, 0.0)
    return make_exam_plan(
        PASSAGES, count, 5, parse_lang_mix("en=0.4,zh-TW=0.2,vi=0.2,id=0.2"), 0.15, mix
    )


def test_plan_is_deterministic_and_a_prefix_of_a_larger_plan():
    small, large = plan(30), plan(60)
    assert small == plan(30)
    assert large[:30] == small
    assert {t.kind for t in large} == {"answer", "refusal"}
    assert {t.gold["source_type"] for t in large} <= {
        "course",
        "food_place",
        "oia_page",
        "academic_calendar",
    }


def test_exam_ids_use_golden_language_codes():
    assert exam_id(task(lang="zh-TW")) == "gen-zh-0042"
    assert exam_id(task(lang="vi")) == "gen-vi-0042"


def test_answerable_accepted_and_row_has_golden_fields():
    seen = []

    def chat(messages):
        seen.append(messages[-1]["content"])
        return "Room E1-201."

    fields, reason = accept_answerable(
        reply("Where are the midterm exams held?", ["E1-201"]), task(), [], chat
    )
    assert reason == "ok"
    assert PASSAGE["passage"] in seen[0] and len(seen) == 1
    row = to_row(task(lang="vi"), fields)
    assert row == {
        "id": "gen-vi-0042",
        "lang": "vi",
        "question": "Where are the midterm exams held?",
        "answerable": True,
        "source_id": "oia-exam",
        "must_contain": ["E1-201"],
        "needs_native_review": True,
        "source_type": "academic_calendar",
        "generated": True,
        "passage_id": "oia-exam@8",
    }


def test_must_contain_not_in_passage_is_rejected():
    fields, reason = accept_answerable(
        reply("Where are the exams?", ["E9-999"]), task(), [], lambda m: "E9-999"
    )
    assert fields is None and reason == "must_contain_not_in_passage"


def test_golden_look_alike_is_rejected():
    blocked = [normalize_question("Where are the midterm exams held?")]
    fields, reason = accept_answerable(
        reply("Where are the midterm exams held", ["E1-201"]),
        task(),
        blocked,
        lambda m: "E1-201",
    )
    assert fields is None and reason == "matches_golden_question"


def test_verification_answer_without_facts_is_rejected():
    fields, reason = accept_answerable(
        reply("Where are the exams held?", ["E1-201"]),
        task(),
        [],
        lambda m: "I do not know.",
    )
    assert fields is None and reason == "verification_answer_lacks_facts"


def test_wrong_language_and_bad_facts_are_rejected():
    _, reason = accept_answerable(
        reply("Where are the exams held?", ["E1-201"]), task(lang="zh-TW"), [], str
    )
    assert reason == "wrong_language"
    _, reason = accept_answerable(
        reply("Where are the exams held?", []), task(), [], str
    )
    assert reason == "bad_must_contain"
    _, reason = accept_answerable(
        reply("Is E1-201 where the exams are held?", ["E1-201"]), task(), [], str
    )
    assert reason == "answer_in_question"


def search(question, limit=5):
    return [{"id": "x", "title": "T", "passage": "text"}] * 2


def test_unanswerable_confirmed_and_unconfirmed():
    raw = json.dumps({"question": "Can I pay the fee in bitcoin?"})
    fields, reason = accept_unanswerable(
        raw, task("refusal"), [], lambda m: '{"answered": false}', search, 2
    )
    assert reason == "ok"
    assert to_row(task("refusal"), fields)["source_type"] == "none"
    assert fields["source_id"] is None and fields["must_contain"] == []
    replies = iter(['{"answered": false}', '{"answered": true}'])
    fields, reason = accept_unanswerable(
        raw, task("refusal"), [], lambda m: next(replies), search, 2
    )
    assert fields is None and reason == "visible_passages_answer"


def test_held_out_questions_cover_golden_and_generated(tmp_path):
    golden, generated = tmp_path / "g.jsonl", tmp_path / "gen.jsonl"
    golden.write_text(json.dumps({"question": "What  is X?"}) + "\n", encoding="utf-8")
    assert held_out_questions(golden, generated) == ["what is x"]
    generated.write_text(
        json.dumps({"question": "Why Y?"}) + "\n" + json.dumps({"question": "WHY y"}),
        encoding="utf-8",
    )
    assert held_out_questions(golden, generated) == ["what is x", "why y"]
