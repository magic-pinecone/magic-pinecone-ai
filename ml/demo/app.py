"""A one-page demo of the assistant: look the question up, then a small model answers.

    cd backend && uv run --frozen python ../ml/demo/app.py --port 8765

The page is a window on the backend's own chain (`app.services.assistant`, the
same code as `POST /api/v1/assistant/ask`): it shows where the question was
sent (documents, a building, or live bus arrivals), the passages the model was
given, the answer, and the checks on it.

Without a model the page shows what was looked up and nothing else. The model
is an OpenAI-compatible endpoint named by `DEMO_BASE_URL` and `DEMO_MODEL`
(for example Ollama's `http://localhost:11434/v1`), with `DEMO_API_KEY` if it
needs one and `DEMO_REASONING_EFFORT=none` to switch off a model's thinking.
Bus questions also need the backend's `TDX_CLIENT_ID` and `TDX_CLIENT_SECRET`.
"""

import argparse
import os
import sys
from pathlib import Path

DEMO_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(DEMO_DIR.parent))

from common import load_golden, student_services  # noqa: E402

# Bus, building and one unanswerable question, to show next to the golden set.
EXTRA_EXAMPLES = (
    ("en", "Where is E6-A203?"),
    ("en", "When is the next 132 bus?"),
    ("zh-TW", "下一班到高鐵的公車幾點？"),
    ("zh-TW", "綜教館在哪裡？"),
    ("vi", "Phòng E6-A203 ở đâu?"),
    ("id", "Bagaimana cara ke bandara naik bus?"),
)


def examples() -> list[dict]:
    extra = [
        {"id": f"demo-{index}", "lang": lang, "question": question, "answerable": True}
        for index, (lang, question) in enumerate(EXTRA_EXAMPLES)
    ]
    golden = [
        {key: item[key] for key in ("id", "lang", "question", "answerable")}
        for item in load_golden()
    ]
    return extra + golden


def model_from_environment():
    """The DEMO_* endpoint as the backend's model client, or None."""
    student_services()  # puts the backend on the import path
    from app.services.assistant import ChatModel

    base_url, model = os.environ.get("DEMO_BASE_URL"), os.environ.get("DEMO_MODEL")
    if not base_url or not model:
        return None
    return ChatModel(
        base_url,
        model,
        os.environ.get("DEMO_API_KEY") or None,
        os.environ.get("DEMO_REASONING_EFFORT") or None,
        timeout=300.0,
    )


def create_app(model=None):
    from fastapi import FastAPI
    from fastapi.responses import HTMLResponse

    services = student_services()
    from app.api.routes.assistant import AskResponse, Question
    from app.services import assistant

    app = FastAPI(
        title="NCU OIA question answering demo", docs_url=None, redoc_url=None
    )

    @app.get("/", response_class=HTMLResponse)
    def page() -> str:
        return (DEMO_DIR / "index.html").read_text(encoding="utf-8")

    @app.get("/api/info")
    def info() -> dict:
        return {
            "model": getattr(model, "name", None),
            "documents": len(services.load_documents()),
            "examples": examples(),
        }

    @app.post("/api/ask", response_model=AskResponse)
    def answer(body: Question):  # a sync route: the model call blocks
        return assistant.ask(body.question, model)

    return app


def main() -> None:
    import uvicorn

    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    model = model_from_environment()
    print(f"model: {model.name if model else 'none (look-up only)'}")
    uvicorn.run(create_app(model), host=args.host, port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
