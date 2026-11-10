"""The demo's question-answering chain, with a scripted model instead of an endpoint."""

from common import student_services
from demo import app as demo
from prompt_format import refusal_text


class ScriptedModel:
    name = "scripted"

    def __init__(self, *replies: str):
        self.replies = list(replies)
        self.calls: list[list[dict]] = []

    def chat(self, messages, temperature=0.0, max_tokens=512):
        self.calls.append(messages)
        return self.replies.pop(0)


def test_without_a_model_it_only_searches():
    result = demo.ask(
        "Cathay Insurance clinic visit reimbursement", student_services(), None
    )
    assert result["passages"] and result["answer"] is None and result["checks"] is None
    assert result["search_query"] == result["question"] and not result["translated"]


def test_english_question_is_searched_as_asked_and_answered_once():
    model = ScriptedModel("- Up to NT$1,000 per visit [1]")
    result = demo.ask(
        "What is the Cathay Insurance clinic visit reimbursement?",
        student_services(),
        model,
    )
    assert len(model.calls) == 1 and not result["translated"]
    assert result["checks"]["cited"] == [1] and result["checks"]["citations_valid"]
    assert "Passages:" in model.calls[0][1]["content"]


def test_other_languages_are_rewritten_as_english_keywords_first():
    model = ScriptedModel("Cathay insurance clinic visit", refusal_text("zh-TW"))
    result = demo.ask("國泰保險每次門診最高可以理賠多少錢？", student_services(), model)
    assert (
        result["translated"]
        and result["search_query"] == "Cathay insurance clinic visit"
    )
    assert result["passages"], (
        "the English keywords find passages the Chinese sentence cannot"
    )
    assert result["checks"]["refused"] and not result["checks"]["unsupported_numbers"]


def test_a_number_missing_from_the_passages_is_reported():
    model = ScriptedModel("- You get NT$987,654 per visit [1]")
    result = demo.ask(
        "What is the Cathay Insurance clinic visit reimbursement?",
        student_services(),
        model,
    )
    assert "987654" in "".join(result["checks"]["unsupported_numbers"]).replace(",", "")


def test_a_citation_of_a_passage_that_is_not_there_is_invalid():
    model = ScriptedModel("- Ask the office [9]")
    result = demo.ask(
        "How many hours can I work with a work permit?", student_services(), model
    )
    assert not result["checks"]["citations_valid"]


def test_an_endpoint_failure_is_reported_not_raised():
    class Broken:
        name = "broken"

        def chat(self, *args, **kwargs):
            raise demo.EndpointError("endpoint answered HTTP 500")

    result = demo.ask("work permit hours", student_services(), Broken())
    assert result["error"] and result["answer"] is None


def test_looks_english():
    assert demo.looks_english("How long is the ARC valid?")
    assert not demo.looks_english("Thẻ cư trú có hiệu lực bao lâu?")
    assert not demo.looks_english("居留證多久到期？")
    assert not demo.looks_english("Berapa lama ARC berlaku?")


def test_office_terms_replace_literal_translations():
    assert demo.office_terms("Guotai insurance limit") == "Cathay insurance limit"
    assert "ARC" in demo.office_terms("How long is the residence permit valid?")
