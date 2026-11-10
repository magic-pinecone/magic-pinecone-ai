import pytest
from checks import (
    contains_fact,
    matches_golden,
    normalize_question,
    strip_reasoning,
    unsupported_numbers,
)

PASSAGE = (
    "Maximum reimbursement of\nNT$1,000\nper day/visit.\n"
    "First Arrival in Taiwan: Sept. 10 → Stay continuously for six months →\n"
    "NHI Enrollment Date: Mar. 10"
)


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("plain answer", "plain answer"),
        ("<think>hmm\nlong</think>\n- a [1]", "- a [1]"),
        ("<|channel>thought\nhmm<channel|>- a [1]", "- a [1]"),
        (
            "<|channel|>analysis<|message|>hmm<|end|><|start|>assistant"
            "<|channel|>final<|message|>- a [1]<|return|>",
            "- a [1]",
        ),
        ("hmm, thinking</think>- a [1]", "- a [1]"),
        ("- a [1]<turn|>", "- a [1]"),
        ("<think>never finished - a [1]", ""),
        ("<|channel|>analysis<|message|>cut off", ""),
        (None, ""),
    ],
)
def test_strip_reasoning(raw, expected):
    assert strip_reasoning(raw) == expected


@pytest.mark.parametrize(
    "answer",
    [
        "- Up to NT$1,000 per visit [1]",
        "- Tối đa 1.000 Đài tệ mỗi lần khám [1]",  # dot as thousands separator
        "- 最高 １０００ 元 [1]",  # full-width digits
        "- NHI starts on Mar. 10 after 6 months [1]",  # "six" allows 6
        "- 3 月 10 日可以加保 [1]",  # "Mar." allows 3
        "1. Up to 1000 [1]\n2. Sept. 10 [1]",  # list enumerators are not facts
    ],
)
def test_supported_numbers_pass(answer):
    assert unsupported_numbers(answer, PASSAGE) == []


@pytest.mark.parametrize(
    ("answer", "missing"),
    [
        ("- Up to NT$2,000 per visit [1]", ["2000"]),
        ("- NHI starts on Apr. 10 [1]", ["apr"]),
        ("- It takes 10 days and costs 100 [2]", ["100"]),
        ("- Call 03-422-7151 [1]", ["422", "7151"]),  # 3 is allowed by "Mar."
    ],
)
def test_unsupported_numbers_found(answer, missing):
    assert unsupported_numbers(answer, PASSAGE) == missing


def test_citation_marker_is_not_a_number():
    assert unsupported_numbers("- per visit [7]", PASSAGE) == []


def test_ignored_strings_are_removed_first():
    answer = "Contact ncuoia@ncu.edu.tw, +886-3-425-4945."
    assert unsupported_numbers(answer, PASSAGE) != []
    assert unsupported_numbers(answer, PASSAGE, ignore=("+886-3-425-4945",)) == []


def test_contains_fact_numbers_are_whole_numbers():
    assert contains_fact("up to 20 hours [1]", "20")
    assert not contains_fact("in 2026 [1]", "20")
    assert not contains_fact("see [20]", "20")
    assert contains_fact("NT$1000", "1,000")
    assert contains_fact("1.000 Đài tệ", "1,000")
    assert not contains_fact("NT$10,000", "1,000")


def test_contains_fact_text_ignores_case_space_and_dash_style():
    assert contains_fact("about 1–2 weeks", "1-2")
    assert contains_fact("每年6月5日前", "6 月 5 日")
    assert contains_fact("the original medical receipt", "Original Medical Receipt")
    assert not contains_fact("a medical receipt", "Original Medical Receipt")


def test_matches_golden():
    golden = [
        normalize_question("How long does it take to review and issue a work permit?"),
        normalize_question("全民健保每學期的費用是多少？"),
    ]
    assert matches_golden(
        "how long does it take to review and issue a work permit", golden
    )
    assert matches_golden(
        "How long does it take to review and issue the work permit?", golden
    )
    assert matches_golden("全民健保每學期的費用是多少", golden)
    assert not matches_golden("Which documents do I need for a work permit?", golden)
    assert not matches_golden("宿舍每學期的費用是多少？", golden)
