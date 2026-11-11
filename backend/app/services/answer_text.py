"""The prompt the answering model sees, and the checks its answer gets.

One definition for everything that builds or reads an answer: the assistant
endpoint here in the backend, and teacher-data generation, training and
evaluation in `ml/` (which load this file by path, see `ml/prompt_format.py`).
A tuned model is therefore served and scored on exactly the format it was
trained on. Standard library only, and nothing from `app`: the training
machine has neither the backend's dependencies nor its settings.

Format of a conversation:

    system:    SYSTEM_PROMPT
    user:      Passages:
               [1] <document title>
               <passage text>

               [2] ...

               Question: <question>
    assistant: - bullet with a source marker [1]
               - ...
               (or the fixed refusal sentence of the question's language)

Checks:

- strip_reasoning: remove a model's thinking channel
- unsupported_numbers: numbers and month names in an answer that the passages
  do not contain
"""

import re
import unicodedata

LANGS = ("en", "zh-TW", "vi", "id")

# Office-level contact of NCU's Office of International Affairs. The e-mail is
# printed in the degree student handbook, the phone number in
# backend/app/data/student_services/contacts.json (tests check both).
OIA_EMAIL = "ncuoia@ncu.edu.tw"
OIA_PHONE = "+886-3-425-4945"
OIA_CONTACT_STRINGS = (OIA_EMAIL, OIA_PHONE)

SYSTEM_PROMPT = (
    "You help international students at National Central University (NCU, "
    "Taiwan) with administrative questions. Answer only from the numbered "
    "passages in the user message; they come from pages of NCU's Office of "
    "International Affairs (OIA).\n"
    "Rules:\n"
    "- Answer in the language of the question.\n"
    "- Answer with short bullet points, each starting with '- '.\n"
    "- End every bullet with the number of the passage it comes from, like [1].\n"
    "- Copy numbers, amounts, dates, URLs and the names of forms, offices and "
    "systems exactly as written in the passages. Never add facts that are not "
    "in the passages.\n"
    "- If the passages do not contain the answer, do not guess. Say in one "
    "sentence, in the language of the question, that you cannot find it in "
    f"the OIA pages you have, and give the OIA contact: {OIA_EMAIL}, "
    f"{OIA_PHONE}. Do not cite any passage in that case."
)

# The refusal the student is trained to give. The vi and id sentences are
# machine-drafted and need review by a native speaker.
REFUSALS = {
    "en": (
        "I can't find this in the OIA pages I have. Please contact the Office "
        f"of International Affairs (OIA): {OIA_EMAIL}, {OIA_PHONE}."
    ),
    "zh-TW": (
        "我手邊的國際事務處資料沒有這項資訊。請直接聯絡國際事務處（OIA）："
        f"{OIA_EMAIL}，{OIA_PHONE}。"
    ),
    "vi": (
        "Tôi không tìm thấy thông tin này trong các trang của OIA mà tôi có. "
        f"Vui lòng liên hệ Văn phòng Quan hệ Quốc tế (OIA): {OIA_EMAIL}, "
        f"{OIA_PHONE}."
    ),
    "id": (
        "Saya tidak menemukan informasi ini di halaman OIA yang saya miliki. "
        f"Silakan hubungi Kantor Urusan Internasional (OIA): {OIA_EMAIL}, "
        f"{OIA_PHONE}."
    ),
}

# [1], [2] and also [1, 2].
CITATION_RE = re.compile(r"\[(\d+(?:\s*,\s*\d+)*)\]")
NO_PASSAGES = "(none)"


def refusal_text(lang: str) -> str:
    return REFUSALS[lang]


def format_passages(passages: list[dict]) -> str:
    """Number the passages from 1. Each needs a `title` and a `passage`."""
    if not passages:
        return NO_PASSAGES
    return "\n\n".join(
        f"[{number}] {item['title']}\n{item['passage']}"
        for number, item in enumerate(passages, start=1)
    )


def build_user_content(question: str, passages: list[dict]) -> str:
    return f"Passages:\n{format_passages(passages)}\n\nQuestion: {question.strip()}"


def build_messages(
    question: str, passages: list[dict], answer: str | None = None
) -> list[dict]:
    """Chat messages for one example; with `answer` it is a training example."""
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": build_user_content(question, passages)},
    ]
    if answer is not None:
        messages.append({"role": "assistant", "content": answer.strip()})
    return messages


def cited_numbers(answer: str) -> list[int]:
    """Passage numbers cited in `answer`, in order, repeats kept."""
    return [
        int(number)
        for group in CITATION_RE.findall(answer)
        for number in group.split(",")
    ]


def is_refusal(answer: str) -> bool:
    """A refusal points to the OIA e-mail and cites no passage."""
    return OIA_EMAIL in answer.casefold() and not cited_numbers(answer)


_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
_THOUSANDS_DOT = re.compile(r"\d{1,3}(?:\.\d{3})+")
_ENUMERATOR = re.compile(r"^\s*\d+[.)]\s+", re.M)

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
