import json

from checks import normalize_question
from make_teacher_data import refusal_confirmed_by_votes
from mix_data import mix, split_user
from prompt_format import SYSTEM_PROMPT, build_messages, format_passages

PASSAGES = [
    {"title": "Work permit", "passage": "Students may work 20 hours a week."},
    {"title": "Question: odd title", "passage": "Line one\n\nQuestion: inside"},
]


def row(id_, question, kind="answer", distractors="random"):
    messages = build_messages(question, PASSAGES, "- answer [1]")
    messages[0]["content"] = "old system prompt"
    return {"id": id_, "kind": kind, "distractors": distractors, "messages": messages}


def test_split_user_returns_passages_and_question():
    content = build_messages("How many hours?", PASSAGES)[1]["content"]
    assert split_user(content) == (format_passages(PASSAGES), "How many hours?")


def test_votes_stop_at_first_answered_reply():
    replies = iter(['{"answered": false}', '{"answered": true}', "never asked"])
    calls = []

    def chat(messages):
        calls.append(messages)
        return next(replies)

    assert refusal_confirmed_by_votes(chat, "p", "q", 3) == (
        False,
        "visible_passages_answer",
    )
    assert len(calls) == 2
    assert "Could a student answer" in calls[0][1]["content"]


def test_mix_rechecks_hard_refusals_and_updates_prompt():
    rows = [
        row("a", "How many hours may I work?"),
        row("a", "Duplicate id"),
        row("b", "Golden look-alike?"),
        row("c", "Unanswered?", "refusal", "retrieved"),
        row("d", "Answered after all?", "refusal", "retrieved"),
        row("e", "Random refusal", "refusal", "random"),
    ]

    def recheck(messages):
        answered = "Answered after all?" in messages[1]["content"]
        return json.dumps({"answered": answered})

    golden = [normalize_question("Golden look-alike?")]
    kept, rejected = mix(rows, golden, recheck, votes=2)
    assert [r["id"] for r in kept] == ["a", "e", "c"]
    assert {r["id"]: r["reason"] for r in rejected} == {
        "a": "duplicate_id",
        "b": "matches_golden_question",
        "d": "visible_passages_answer",
    }
    assert all(r["messages"][0]["content"] == SYSTEM_PROMPT for r in kept)
    assert rows[0]["messages"][0]["content"] == "old system prompt"


def test_mix_without_votes_keeps_hard_refusals():
    rows = [row("c", "Unanswered?", "refusal", "retrieved")]
    kept, rejected = mix(rows, [], lambda messages: "unused", votes=0)
    assert [r["id"] for r in kept] == ["c"] and rejected == []


def test_mix_drops_questions_of_the_generated_set(tmp_path):
    from common import held_out_questions

    golden, generated = tmp_path / "g.jsonl", tmp_path / "gen.jsonl"
    golden.write_text("", encoding="utf-8")
    generated.write_text(json.dumps({"question": "Generated one?"}), encoding="utf-8")
    held_out = held_out_questions(golden, generated)
    kept, rejected = mix(
        [row("a", "Generated one?"), row("b", "Something else?")], held_out, None, 0
    )
    assert [r["id"] for r in kept] == ["b"]
    assert rejected == [{"id": "a", "reason": "matches_golden_question"}]
