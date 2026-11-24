import json
from collections import Counter

import pytest
from common import knowledge_services
from make_teacher_data import (
    DEFAULT_TYPE_MIX,
    Task,
    clean_example,
    corpus_passages,
    hard_distractors,
    in_language,
    lines_overlap,
    make_plan,
    parse_lang_mix,
    parse_type_mix,
    refusal_confirmed,
    swap_distractors,
    teacher_messages,
)

MIX = parse_lang_mix("en=1")
PASSAGES = corpus_passages(knowledge_services())
AVAILABLE = {p["source_type"] for p in PASSAGES}
TYPE_MIX = parse_type_mix(DEFAULT_TYPE_MIX, AVAILABLE)


def plan(count=600, **kwargs):
    return make_plan(
        PASSAGES,
        count,
        3,
        MIX,
        0.18,
        1,
        type_mix=TYPE_MIX,
        multi_rate=0.1,
        hard_rate=0.7,
        **kwargs,
    )


def test_oia_and_gov_guides_are_at_least_half_and_every_type_appears():
    tasks = plan(1500)
    shares = Counter(t.gold["source_type"] for t in tasks)
    assert (shares["oia_page"] + shares["gov_guide"]) / len(tasks) >= 0.5
    assert set(shares) == set(TYPE_MIX)
    assert min(shares.values()) >= 10


def test_type_mix_rejects_a_mix_with_too_little_oia_and_gov_guide():
    with pytest.raises(ValueError):
        parse_type_mix("oia_page=0.2,credits=0.8", AVAILABLE)
    with pytest.raises(ValueError):
        parse_type_mix("oia_page=1,nonsense=1", AVAILABLE)


def test_plan_prefix_does_not_depend_on_count():
    assert plan(120) == plan(300)[:120]


def test_task_kinds_and_hard_share():
    tasks = plan(1000)
    kinds = Counter(t.kind for t in tasks)
    assert 0.06 <= kinds["multi"] / len(tasks) <= 0.14
    assert 0.12 <= kinds["refusal"] / len(tasks) <= 0.25
    answers = [t for t in tasks if t.kind == "answer"]
    assert 0.6 <= sum(t.hard for t in answers) / len(answers) <= 0.8
    assert not any(t.hard for t in tasks if t.kind != "answer")


def test_multi_task_shows_two_different_answering_passages():
    for t in (t for t in plan(400) if t.kind == "multi"):
        assert t.shown[t.gold_number - 1] is t.gold
        assert t.shown[t.second_number - 1] is t.second
        assert t.gold_number != t.second_number
        assert not lines_overlap(t.gold, t.second)
        prompt = teacher_messages(t)[1]["content"]
        assert f"passage [{t.gold_number}] and passage [{t.second_number}]" in prompt


def multi_task():
    gold = {
        "id": "a",
        "title": "A",
        "start": 0,
        "passage": "Fee is NT$100\nPay at the office",
    }
    second = {
        "id": "b",
        "title": "B",
        "start": 0,
        "passage": "Office hours 9:00-17:00",
    }
    other = {"id": "c", "title": "C", "start": 0, "passage": "Unrelated 5"}
    return Task("m", "multi", "en", 0, gold, (other, gold, second), 2, second, 3)


def reply(answer):
    question = "What does the fee cost and when is the office open?"
    return json.dumps({"question": question, "answer": answer})


def test_multi_answer_must_cite_both_passages():
    ok, reason = clean_example(
        reply("- NT$100 [2]\n- 9:00-17:00 [3]"), multi_task(), []
    )
    assert reason == "ok" and ok
    assert clean_example(reply("- NT$100 [2]"), multi_task(), []) == (
        None,
        "second_passage_not_cited",
    )
    assert clean_example(reply("- 9:00-17:00 [3]"), multi_task(), []) == (
        None,
        "answer_passage_not_cited",
    )


def test_hard_distractors_come_from_the_search_and_skip_the_answer():
    gold = {"id": "a", "title": "A", "start": 0, "passage": "x1\nx2\nFee 100"}
    keep = {"id": "k", "title": "K", "start": 0, "passage": "kept"}
    near = {"id": "n", "title": "N", "start": 8, "passage": "near\nbest match"}
    task = Task("t", "answer", "en", 0, gold, (keep, gold), 2, hard=True)

    def search(question, limit):
        assert question == "How much is the fee?"
        return [
            {"id": "a", "title": "A", "passage": "x2\nFee 100"},
            {**near},
        ]

    shown = hard_distractors(
        task, "How much is the fee?", search, {("n", near["passage"]): 8}
    )
    assert shown[1] is gold
    assert shown[0]["id"] == "n" and shown[0]["start"] == 8


def test_swap_rejects_a_number_that_the_new_distractors_do_not_support():
    gold = {"id": "a", "title": "A", "start": 0, "passage": "Fee 100"}
    other = {"id": "k", "title": "K", "start": 0, "passage": "Maybe 77"}
    task = Task("t", "answer", "en", 0, gold, (gold, other), 1, hard=True)
    example = {"question": "q", "answer": "- 100 [1]\n- 77 [1]"}

    def search(question, limit):
        return [{"id": "z", "title": "Z", "passage": "Nothing here"}]

    swapped, reason = swap_distractors(task, example, search, {})
    assert swapped is task and reason.startswith("number_not_in_passages_after_swap")


def test_hard_refusal_shows_search_results_from_other_documents_only():
    gold = {"id": "a", "title": "A", "start": 0, "passage": "Fee 100"}
    shown = (
        {"id": "r1", "title": "R1", "start": 0, "passage": "random one"},
        {"id": "r2", "title": "R2", "start": 0, "passage": "random two"},
    )
    task = Task("t", "refusal", "en", 0, gold, shown, 0, hard=True)
    example = {"question": "How much is the fee?", "answer": "refused"}

    def search(question, limit):
        return [
            {"id": "a", "title": "A", "passage": "Other part of A"},
            {"id": "b", "title": "B", "passage": "Fees in general"},
            {"id": "c", "title": "C", "passage": "Payment desk 0800-000-000"},
        ]

    swapped, reason = swap_distractors(task, example, search, {})
    assert reason == "ok"
    assert [p["id"] for p in swapped.shown] == ["b", "c"]


def test_refusal_is_kept_only_when_the_teacher_finds_no_answer():
    assert refusal_confirmed('{"answered": false}') == (True, "ok")
    assert refusal_confirmed('{"answered": true}') == (
        False,
        "visible_passages_answer",
    )
    assert refusal_confirmed("maybe") == (False, "verify_not_json")


def test_plan_marks_hard_refusals_only_when_asked():
    assert not any(t.hard for t in plan() if t.kind == "refusal")
    hard = [t.hard for t in plan(hard_refusal_rate=1.0) if t.kind == "refusal"]
    assert hard and all(hard)


def test_answer_language():
    assert in_language("- The fine is NT$150,000 (新臺幣十五萬元) [2]", "en")
    assert not in_language("- The fine is 新臺幣三萬元以上十五萬元以下之罰鍰 [2]", "en")
    assert in_language("- 罰鍰新臺幣三萬元以上 [1]", "zh-TW")
    assert not in_language("- The fine is NT$150,000 [1]", "zh-TW")
