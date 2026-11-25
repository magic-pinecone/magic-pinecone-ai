"""Pure text checks shared by data cleaning and scoring. Standard library only.

- strip_reasoning: remove a teacher's or student's thinking channel
- unsupported_numbers: numbers and month names in an answer that the passages
  do not contain
- contains_fact: is a golden `must_contain` string present in an answer
- facts_held, holds_answer: how much of a golden item's answer a text has
- matches_golden: is a generated question (nearly) a golden question
"""

import re
import unicodedata
from difflib import SequenceMatcher

from prompt_format import answer_text

GOLDEN_SIMILARITY = 0.85

# Shared with the assistant endpoint: backend/app/services/answer_text.py.
strip_reasoning = answer_text.strip_reasoning
normalize_number = answer_text.normalize_number
numbers = answer_text.numbers
months = answer_text.months
allowed_numbers = answer_text.allowed_numbers
unsupported_numbers = answer_text.unsupported_numbers
_NUMBER = answer_text._NUMBER
_nfkc = answer_text._nfkc

_DASHES = str.maketrans({"–": "-", "—": "-", "‐": "-", "‑": "-", "−": "-"})


_NUMBER_WORDS = dict(
    zip(
        "one two three four five six seven eight nine ten eleven twelve".split(),
        map(str, range(1, 13)),
        strict=True,
    )
)
_NUMBER_WORD = re.compile(r"\b(" + "|".join(_NUMBER_WORDS) + r")\b")
_RANGE = re.compile(r"(?<=\d)\s*(?:-|~|to|至|到)\s*(?=\d)")


def spell_digits(text: str) -> str:
    """English number words one to twelve written as digits ("four" -> "4")."""
    return _NUMBER_WORD.sub(lambda m: _NUMBER_WORDS[m.group(1)], text)


def squash(text: str) -> str:
    """Case, width, dash style, range words ("9 to 5" = "9-5"), English number
    words and whitespace removed or unified, for substring tests."""
    text = spell_digits(_nfkc(text).casefold().translate(_DASHES))
    return re.sub(r"\s+", "", _RANGE.sub("-", text))


def contains_fact(answer: str, fact: str) -> bool:
    """Is the golden `must_contain` string `fact` in `answer`?

    A fact that is a bare number has to appear as a whole number ("20" is not
    found in "2026", "1,000" is found in "1000" and "five" counts as "5");
    anything else is a substring test that ignores case, whitespace, dash
    style, range words and English number words (see `squash`).
    """
    if _NUMBER.fullmatch(fact.strip()):
        return normalize_number(fact.strip()) in numbers(
            spell_digits(_nfkc(answer).casefold())
        )
    return squash(fact) in squash(answer)


def facts_held(text: str, item: dict) -> int:
    """How many of the item's requirements `text` meets.

    Each `must_contain` string counts for one, and so does `must_contain_any`
    as a whole when at least one of its spellings is found.
    """
    held = sum(contains_fact(text, fact) for fact in item["must_contain"])
    spellings = item.get("must_contain_any", [])
    return held + any(contains_fact(text, fact) for fact in spellings)


def holds_answer(text: str, item: dict) -> bool:
    """Has `text` every `must_contain` string and, when the item lists
    `must_contain_any`, at least one of those spellings?

    The one test for answers, prompt passages and retrieved passages.
    """
    return facts_held(text, item) == len(item["must_contain"]) + bool(
        item.get("must_contain_any")
    )


def normalize_question(text: str) -> str:
    kept = "".join(
        " " if unicodedata.category(char)[0] in "PSZ" else char
        for char in _nfkc(text).casefold()
    )
    return " ".join(kept.split())


def matches_golden(
    question: str, golden_normalized: list[str], threshold: float = GOLDEN_SIMILARITY
) -> bool:
    """True when `question` equals a golden question or is nearly the same.

    `golden_normalized` are golden questions passed through normalize_question.
    """
    candidate = normalize_question(question)
    matcher = SequenceMatcher(None, candidate)
    for golden in golden_normalized:
        if candidate == golden:
            return True
        matcher.set_seq2(golden)
        if (
            matcher.real_quick_ratio() >= threshold
            and matcher.quick_ratio() >= threshold
            and matcher.ratio() >= threshold
        ):
            return True
    return False
