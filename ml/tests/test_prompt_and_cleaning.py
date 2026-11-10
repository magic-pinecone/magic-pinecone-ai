import json

import pytest
from checks import normalize_question
from common import BACKEND_DIR, load_golden, student_services
from make_teacher_data import (
    FakeTeacher,
    Task,
    clean_example,
    corpus_passages,
    make_plan,
    parse_lang_mix,
    teacher_messages,
    to_row,
)
from prompt_format import (
    LANGS,
    OIA_EMAIL,
    OIA_PHONE,
    REFUSALS,
    build_messages,
    cited_numbers,
    is_refusal,
)

GOLD = {
    "id": "oia-work-permit",
    "title": "Work permit for international degree students",
    "start": 25,
    "passage": "It takes about 1-2 weeks working days to review and issue the permit.\n"
    "Except during summer and winter vacations, the maximum working hours are 20 hours per week.",
}
OTHER = {
    "id": "oia-insurance",
    "title": "Medical insurance for international degree students",
    "start": 5,
    "passage": "Maximum reimbursement of\nNT$1,000\nper day/visit.",
}
GOLDEN = [
    normalize_question(
        "With a work permit, how many hours per week can I work during the semester?"
    )
]


def task(kind="answer", lang="en"):
    if kind == "refusal":
        return Task("t", kind, lang, 0, GOLD, (OTHER, OTHER), 0)
    return Task("t", kind, lang, 0, GOLD, (OTHER, GOLD), 2)


def reply(
    question="What is the weekly limit on working hours?",
    answer="- 20 hours per week [2]",
):
    return json.dumps({"question": question, "answer": answer})


def test_contact_matches_backend_data():
    contacts = (BACKEND_DIR / "app/data/student_services/contacts.json").read_text(
        encoding="utf-8"
    )
    assert OIA_PHONE in contacts
    handbook = next(
        d
        for d in student_services().load_documents()
        if d.id == "oia-degree-student-handbook"
    )
    assert OIA_EMAIL in "\n".join(handbook.lines)


def test_refusals_cover_every_language_and_are_detected():
    assert set(REFUSALS) == set(LANGS)
    for text in REFUSALS.values():
        assert OIA_EMAIL in text and OIA_PHONE in text
        assert is_refusal(text)
    assert not is_refusal("- Write to ncuoia@ncu.edu.tw [1]")
    assert not is_refusal("- 20 hours per week [1]")


def test_build_messages_format():
    messages = build_messages("  How many hours? ", [OTHER, GOLD], "- 20 [2]")
    assert [m["role"] for m in messages] == ["system", "user", "assistant"]
    user = messages[1]["content"]
    assert user.startswith("Passages:\n[1] Medical insurance")
    assert "\n\n[2] Work permit for international degree students\nIt takes" in user
    assert user.endswith("\n\nQuestion: How many hours?")
    assert "(none)" in build_messages("q", [])[1]["content"]
    assert cited_numbers("- a [1]\n- b [2, 3] and [1]") == [1, 2, 3, 1]


def test_clean_accepts_good_answer_and_normalizes_bullets():
    example, reason = clean_example(
        "<think>plan</think>"
        + reply(answer="* 20 hours per week [2]\n• about 1-2 weeks [2]"),
        task(),
        GOLDEN,
    )
    assert reason == "ok"
    assert example["answer"] == "- 20 hours per week [2]\n- about 1-2 weeks [2]"


@pytest.mark.parametrize(
    ("raw", "reason"),
    [
        ("no json here", "not_json"),
        ("<think>cut off " + reply(), "not_json"),
        ('{"skip": true}', "teacher_skipped"),
        (reply(question=" "), "no_question"),
        (reply(question="每週最多可以工作幾小時？"), "wrong_language"),
        (
            reply(
                question="With a work permit how many hours per week can I work during the semester"
            ),
            "matches_golden_question",
        ),
        (reply(answer=""), "no_answer"),
        (reply(answer="You may work 20 hours per week [2]"), "not_bulleted"),
        (reply(answer="- 20 hours per week"), "bullet_without_citation"),
        (
            reply(answer="- 20 hours per week [2]\n- only in term time"),
            "bullet_without_citation",
        ),
        (reply(answer="- 20 hours per week [3]"), "citation_out_of_range"),
        (reply(answer="- NT$1,000 per visit [1]"), "answer_passage_not_cited"),
        (reply(answer="- 25 hours per week [2]"), "number_not_in_passages: 25"),
        (reply(answer="- Ask ncuoia@ncu.edu.tw [2]"), "answer_is_refusal"),
    ],
)
def test_clean_rejects(raw, reason):
    assert clean_example(raw, task(), GOLDEN) == (None, reason)


def test_refusal_example_uses_the_fixed_sentence():
    raw = json.dumps(
        {"question": "Tôi được làm bao nhiêu giờ mỗi tuần?", "answer": "- 20 [1]"}
    )
    example, reason = clean_example(raw, task("refusal", "vi"), GOLDEN)
    assert reason == "ok" and example["answer"] == REFUSALS["vi"]
    row = to_row(task("refusal", "vi"), example, "teacher-a")
    assert row["teacher"] == "teacher-a" and row["kind"] == "refusal"
    assert GOLD["passage"] not in row["messages"][1]["content"]
    assert is_refusal(row["messages"][2]["content"])


def test_plan_is_deterministic_and_well_formed():
    services = student_services()
    passages = corpus_passages(services)
    assert passages[1]["start"] == services.PASSAGE_STEP
    assert len(passages[0]["passage"].split("\n")) <= services.PASSAGE_LINES
    mix = parse_lang_mix("en=0.5,zh-TW=0.2,vi=0.15,id=0.15")
    plan = make_plan(passages, 300, 1, mix, 0.18, teachers=2)
    assert plan == make_plan(passages, 300, 1, mix, 0.18, teachers=2)
    assert plan[:50] == make_plan(passages, 50, 1, mix, 0.18, teachers=2)
    assert len({t.id for t in plan}) == 300 and {t.teacher for t in plan} == {0, 1}
    refusals = [t for t in plan if t.kind == "refusal"]
    assert 0.12 <= len(refusals) / len(plan) <= 0.25
    for t in plan:
        other_docs = [p for p in t.shown if p["id"] != t.gold["id"]]
        assert len(other_docs) >= 1  # at least one distractor from another document
        if t.kind == "refusal":
            assert t.gold_number == 0 and len(other_docs) == len(t.shown)
        else:
            assert t.shown[t.gold_number - 1] is t.gold
    assert sum(t.lang == "en" for t in plan) > len(plan) * 0.4
    with pytest.raises(ValueError):
        parse_lang_mix("fr=1")


def test_fake_teacher_round_trip_never_leaks_golden_questions():
    passages = corpus_passages(student_services())
    golden = [normalize_question(i["question"]) for i in load_golden()]
    teacher, kept = FakeTeacher(), 0
    for t in make_plan(passages, 60, 0, parse_lang_mix("en=1"), 0.2, teachers=1):
        example, _ = clean_example(teacher.chat(teacher_messages(t)), t, golden)
        if example:
            kept += 1
            row = to_row(t, example, teacher.name)
            assert max(cited_numbers(row["messages"][2]["content"]), default=0) <= len(
                t.shown
            )
    assert 0 < kept < 60  # the fake teacher's faulty replies are filtered out
