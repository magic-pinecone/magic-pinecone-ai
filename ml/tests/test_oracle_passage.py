import pytest
from checks import holds_answer
from common import load_golden, student_services
from eval_generation import oracle_passage

GOLDEN = {item["id"]: item for item in load_golden()}

# Questions whose number is also elsewhere on the page: "20" inside the file
# name std20230311, "15" in the dates of the amendment history.
ANSWER_SENTENCE = {
    "en-006": "the maximum working hours are 20 hours per week",
    "vi-004": "the maximum working hours are 20 hours per week",
    "id-004": "the maximum working hours are 20 hours per week",
    "zh-004": "the maximum working hours are 20 hours per week",
    "zh-011": "若受獎生離開學校超過 15 日",
}


@pytest.mark.parametrize("item_id", sorted(ANSWER_SENTENCE))
def test_oracle_passage_has_the_answer_sentence(item_id):
    passage = oracle_passage(student_services(), GOLDEN[item_id])["passage"]
    assert ANSWER_SENTENCE[item_id] in passage


def test_a_number_inside_a_longer_number_does_not_count():
    passage = oracle_passage(student_services(), GOLDEN["en-006"])["passage"]
    assert "std20230311" not in passage


def test_oracle_passage_of_an_item_with_must_contain_any():
    services = student_services()
    assert (
        "completely excluded" in oracle_passage(services, GOLDEN["en-005"])["passage"]
    )
    assert "less than 30 days" in oracle_passage(services, GOLDEN["zh-003"])["passage"]


def test_every_answerable_question_has_one_passage_with_all_facts():
    services = student_services()
    lacking = [
        item["id"]
        for item in GOLDEN.values()
        if item["answerable"]
        and not holds_answer(oracle_passage(services, item)["passage"], item)
    ]
    assert lacking == []
