"""The one definition of the prompt the student model sees.

Teacher-data generation, training and evaluation all build their chat messages
here, so a tuned student is evaluated on exactly the format it was trained on.
Standard library only: this module is imported on machines without the backend.

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
"""

import re

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
