"""A one-page demo of the question-answering chain: search, then a small model.

    cd backend && uv run --frozen python ../ml/demo/app.py --port 8765

Without a model the page shows the passages the search returns and nothing
else. With `DEMO_BASE_URL` and `DEMO_MODEL` (an OpenAI-compatible endpoint, for
example Ollama's `http://localhost:11434/v1`; `DEMO_API_KEY` if it needs one;
`DEMO_REASONING_EFFORT=none` to switch off a model's thinking)
the model first translates a question that is not English, for the search,
then answers from the passages in the prompt format the students are trained
on. Every answer is checked the way `eval_generation.py` checks it: the passage
numbers it cites exist, and its numbers appear in the passages.
"""

import argparse
import os
import re
import sys
import time
from pathlib import Path

DEMO_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(DEMO_DIR.parent))

from checks import strip_reasoning, unsupported_numbers  # noqa: E402
from common import (  # noqa: E402
    ChatClient,
    EndpointError,
    load_golden,
    student_services,
)
from prompt_format import (  # noqa: E402
    OIA_CONTACT_STRINGS,
    build_messages,
    cited_numbers,
    is_refusal,
)

TOP_K = 3
MAX_QUESTION_CHARS = 500
KEYWORDS_PROMPT = (
    "Translate the student's question into English. Reply with the translation "
    "only, on one line."
)
# The office's own names for things a literal translation calls something else.
# Done here, not in the prompt: a 2B model copies a glossary into every reply.
OFFICE_TERMS = (
    ("guotai", "Cathay"),
    ("cathay life", "Cathay"),
    ("residence permit", "ARC residence permit"),
    ("residence card", "ARC residence"),
    ("national health insurance", "NHI National Health Insurance"),
    ("health insurance card", "NHI card"),
)


ENGLISH_WORDS = frozenset(
    "a an and are can do does for how i in is long many much my of the to what when where "
    "which who will with".split()
)


def looks_english(text: str) -> bool:
    """True when the question can go to the English keyword search as it is.

    Indonesian is written in plain ASCII too, so ASCII letters are not enough:
    the question also has to contain a common English function word.
    """
    letters = [char for char in text if char.isalpha()]
    if not letters or not all(char.isascii() for char in letters):
        return False
    words = "".join(char if char.isalpha() else " " for char in text.casefold()).split()
    return any(word in ENGLISH_WORDS for word in words)


def office_terms(text: str) -> str:
    """`text` with the office's names put in place of literal translations."""
    for literal, name in OFFICE_TERMS:
        text = re.sub(re.escape(literal), name, text, flags=re.IGNORECASE)
    return text


def search_keywords(question: str, model) -> str | None:
    """English keywords for a non-English question, or None to search as asked."""
    if model is None or looks_english(question):
        return None
    reply = model.chat(
        [
            {"role": "system", "content": KEYWORDS_PROMPT},
            {"role": "user", "content": question},
        ],
        max_tokens=80,
    )
    return office_terms(" ".join(strip_reasoning(reply).split())[:300]) or None


def check_answer(answer: str, question: str, passages: list[dict]) -> dict:
    """What a reader can verify without knowing the subject."""
    context = "\n".join(p["title"] + "\n" + p["passage"] for p in passages)
    cited = cited_numbers(answer)
    return {
        "refused": is_refusal(answer),
        "cited": sorted(set(cited)),
        "citations_valid": bool(cited) and all(1 <= n <= len(passages) for n in cited),
        "unsupported_numbers": unsupported_numbers(
            answer, context + "\n" + question, ignore=OIA_CONTACT_STRINGS
        ),
    }


def ask(question: str, services, model) -> dict:
    """Search and, with a model, answer. `model` needs `.chat()` and `.name`."""
    question = question.strip()[:MAX_QUESTION_CHARS]
    result = {
        "question": question,
        "search_query": question,
        "translated": False,
        "passages": [],
        "model": getattr(model, "name", None),
        "answer": None,
        "checks": None,
        "error": None,
        "seconds": None,
    }
    if not question:
        result["error"] = "empty question"
        return result
    started = time.monotonic()
    try:
        keywords = search_keywords(question, model)
        if keywords:
            result["search_query"], result["translated"] = keywords, True
        found = services.search(result["search_query"], limit=TOP_K)
        result["passages"] = [
            {key: row[key] for key in ("id", "title", "url", "fetched_on", "passage")}
            for row in found
        ]
        if model is not None:
            answer = strip_reasoning(
                model.chat(build_messages(question, result["passages"]))
            )
            result["answer"] = answer
            result["checks"] = check_answer(answer, question, result["passages"])
    except EndpointError as error:
        result["error"] = f"model endpoint: {error}"
    result["seconds"] = round(time.monotonic() - started, 1)
    return result


def examples() -> list[dict]:
    return [
        {key: item[key] for key in ("id", "lang", "question", "answerable")}
        for item in load_golden()
    ]


def create_app(model=None):
    from fastapi import FastAPI
    from fastapi.responses import HTMLResponse
    from pydantic import BaseModel, Field

    class Question(BaseModel):
        question: str = Field(max_length=MAX_QUESTION_CHARS)

    services = student_services()
    app = FastAPI(
        title="NCU OIA question answering demo", docs_url=None, redoc_url=None
    )

    @app.get("/", response_class=HTMLResponse)
    def page() -> str:
        return (DEMO_DIR / "index.html").read_text(encoding="utf-8")

    @app.get("/api/info")
    def info() -> dict:
        documents = services.load_documents()
        return {
            "model": getattr(model, "name", None),
            "documents": len(documents),
            "examples": examples(),
        }

    @app.post("/api/ask")
    def answer(body: Question) -> dict:  # a sync route: the model call blocks
        return ask(body.question, services, model)

    return app


def model_from_environment():
    """The DEMO_* endpoint, or None when it is not configured."""
    try:
        effort = os.environ.get("DEMO_REASONING_EFFORT", "")
        extra = {"reasoning_effort": effort} if effort else None
        return ChatClient("DEMO", timeout=300.0, retries=1, extra=extra)
    except EndpointError:
        return None


def main() -> None:
    import uvicorn

    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    model = model_from_environment()
    print(f"model: {model.name if model else 'none (search only)'}")
    uvicorn.run(create_app(model), host=args.host, port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
