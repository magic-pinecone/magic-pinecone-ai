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

from prompt_format import CITATION_RE

GOLDEN_SIMILARITY = 0.85

_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
_THOUSANDS_DOT = re.compile(r"\d{1,3}(?:\.\d{3})+")
_ENUMERATOR = re.compile(r"^\s*\d+[.)]\s+", re.M)
_DASHES = str.maketrans({"–": "-", "—": "-", "‐": "-", "‑": "-", "−": "-"})

_MONTHS = (
    ("january", "jan"),
    ("february", "feb"),
    ("march", "mar"),
    ("april", "apr"),
    ("may",),
    ("june", "jun"),
    ("july", "jul"),
    ("august", "aug"),
    ("september", "sept", "sep"),
    ("october", "oct"),
    ("november", "nov"),
    ("december", "dec"),
)
_MONTH_INDEX = {
    name: index for index, names in enumerate(_MONTHS, start=1) for name in names
}
_MONTH_WORD = re.compile(
    r"(?<![a-z])("
    + "|".join(sorted(_MONTH_INDEX, key=len, reverse=True))
    + r")(?![a-z])"
)
_NUMBER_WORDS = {
    word: str(value)
    for value, word in enumerate(
        "zero one two three four five six seven eight nine ten eleven twelve".split()
    )
}
_NUMBER_WORD = re.compile(r"(?<![a-z])(" + "|".join(_NUMBER_WORDS) + r")(?![a-z])")
_CJK_DIGITS = {
    "一": "1", "二": "2", "兩": "2", "三": "3", "四": "4", "五": "5",
    "六": "6", "七": "7", "八": "8", "九": "9", "十": "10",
}  # fmt: skip

# Reasoning channels: <think> style tags, Gemma 4's thought channel and the
# harmony format of gpt-oss.
_THINK_BLOCKS = (
    re.compile(r"<(think|thinking|reasoning|thought)>.*?</\1>", re.S | re.I),
    re.compile(r"<\|channel>thought.*?<channel\|>", re.S),
)
_THINK_OPEN = re.compile(
    r"<(think|thinking|reasoning|thought)>|<\|channel>thought", re.I
)
_THINK_CLOSE = re.compile(r"</(think|thinking|reasoning|thought)>|<channel\|>", re.I)
_HARMONY_FINAL = "<|channel|>final<|message|>"
_HARMONY_ANALYSIS = re.compile(r"<\|channel\|>(analysis|commentary)")
_SPECIAL_TOKEN = re.compile(r"<\|[a-z_]+\|>|<turn\|>|<end_of_turn>|<eos>")


def strip_reasoning(text: str | None) -> str:
    """The final answer without any thinking channel; '' if only thinking is left.

    An opened but never closed thinking block means the output was cut off
    inside the reasoning, so nothing of it is kept.
    """
    if not text:
        return ""
    if _HARMONY_FINAL in text:
        text = text.rsplit(_HARMONY_FINAL, 1)[1]
    elif _HARMONY_ANALYSIS.search(text):
        return ""
    for pattern in _THINK_BLOCKS:
        text = pattern.sub("", text)
    closes = list(_THINK_CLOSE.finditer(text))
    if closes:  # the server removed the opening tag but left the closing one
        text = text[closes[-1].end() :]
    if _THINK_OPEN.search(text):
        return ""
    return _SPECIAL_TOKEN.sub("", text).strip()


def _nfkc(text: str) -> str:
    return unicodedata.normalize("NFKC", text)


def normalize_number(token: str) -> str:
    """'1,000' and '1.000' and '1000' are the same number; '4.5' stays '4.5'."""
    token = token.replace(",", "")
    if _THOUSANDS_DOT.fullmatch(token):
        token = token.replace(".", "")
    if token.isdigit():
        token = token.lstrip("0") or "0"
    return token


def numbers(text: str) -> list[str]:
    """Normalized numbers in `text`, citation markers such as [2] left out."""
    text = CITATION_RE.sub(" ", _nfkc(text))
    return [normalize_number(token) for token in _NUMBER.findall(text)]


def months(text: str) -> set[int]:
    return {_MONTH_INDEX[name] for name in _MONTH_WORD.findall(_nfkc(text).casefold())}


def allowed_numbers(source_text: str) -> set[str]:
    """Numbers an answer may use: those written in the passages, in any form.

    Besides the digits themselves this allows the number of a month named in
    the passages ("Mar." allows 3, for answers in other languages) and the
    digit of a spelled-out small number ("six months" allows 6).
    """
    folded = _nfkc(source_text).casefold()
    allowed = set(numbers(source_text))
    allowed |= {str(month) for month in months(source_text)}
    allowed |= {_NUMBER_WORDS[word] for word in _NUMBER_WORD.findall(folded)}
    allowed |= {digit for char, digit in _CJK_DIGITS.items() if char in folded}
    return allowed


def unsupported_numbers(
    answer: str, source_text: str, ignore: tuple[str, ...] = ()
) -> list[str]:
    """Numbers and English month names in `answer` that `source_text` lacks.

    `ignore` holds strings removed from the answer first (the OIA contact of a
    refusal). List enumerators at the start of a line are not counted.
    """
    for item in ignore:
        answer = answer.replace(item, " ")
    answer = _ENUMERATOR.sub("", answer)
    allowed = allowed_numbers(source_text)
    missing = [number for number in numbers(answer) if number not in allowed]
    source_months = months(source_text)
    for name in _MONTH_WORD.findall(_nfkc(answer).casefold()):
        # "may" is far more often the verb than the month.
        if name != "may" and _MONTH_INDEX[name] not in source_months:
            missing.append(name)
    return list(dict.fromkeys(missing))


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
