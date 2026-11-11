"""Pure text checks shared by data cleaning and scoring. Standard library only.

- strip_reasoning: remove a teacher's or student's thinking channel
- unsupported_numbers: numbers and month names in an answer that the passages
  do not contain
- contains_fact: is a golden `must_contain` string present in an answer
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


def squash(text: str) -> str:
    """Case, width, dash style and whitespace removed, for substring tests."""
    return re.sub(r"\s+", "", _nfkc(text).casefold().translate(_DASHES))


def contains_fact(answer: str, fact: str) -> bool:
    """Is the golden `must_contain` string `fact` in `answer`?

    A fact that is a bare number has to appear as a whole number ("20" is not
    found in "2026", "1,000" is found in "1000"); anything else is a substring
    test that ignores case, whitespace and dash style.
    """
    if _NUMBER.fullmatch(fact.strip()):
        return normalize_number(fact.strip()) in numbers(answer)
    return squash(fact) in squash(answer)


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
    for golden in golden_normalized:
        if candidate == golden:
            return True
        if SequenceMatcher(None, candidate, golden).ratio() >= threshold:
            return True
    return False
