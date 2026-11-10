"""Paths, access to the backend's retrieval service, golden set, chat client."""

import json
import os
import sys
import time
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent
BACKEND_DIR = ML_DIR.parent / "backend"
GOLDEN_PATH = ML_DIR / "golden" / "questions.jsonl"
RESULTS_DIR = ML_DIR / "results"


def student_services():
    """The backend's passage search module (`app.services.student_services`).

    Works from any working directory as long as the interpreter has the
    backend's dependencies, i.e. `cd backend && uv run --frozen python ...`.
    """
    if str(BACKEND_DIR) not in sys.path:
        sys.path.insert(0, str(BACKEND_DIR))
    from app.services import student_services as module

    return module


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def append_jsonl(path: Path, row: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def load_golden(path: Path = GOLDEN_PATH) -> list[dict]:
    return read_jsonl(path)


def expected_sources(item: dict) -> set[str]:
    """Documents that hold the answer: `source_id` plus any `alt_source_ids`."""
    if not item["answerable"]:
        return set()
    return {item["source_id"], *item.get("alt_source_ids", [])}


def markdown_table(header: list[str], rows: list[list]) -> str:
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(lines)


def rate(hits: int, total: int) -> str:
    return f"{hits}/{total} ({hits / total:.0%})" if total else "n/a"


class EndpointError(RuntimeError):
    pass


class ChatClient:
    """Minimal client for an OpenAI-compatible `/chat/completions` endpoint.

    URL, key and model come from the environment variables `<PREFIX>_BASE_URL`
    (including the `/v1` part), `<PREFIX>_API_KEY` (optional) and
    `<PREFIX>_MODEL`. Neither the URL nor the key is ever printed; errors
    report only the status code or the exception type.
    """

    def __init__(
        self,
        prefix: str,
        timeout: float = 180.0,
        retries: int = 3,
        extra: dict | None = None,
    ):
        import httpx  # a backend dependency; not needed for --dry-run

        base_url = os.environ.get(f"{prefix}_BASE_URL", "").rstrip("/")
        self.model = os.environ.get(f"{prefix}_MODEL", "")
        if not base_url or not self.model:
            raise EndpointError(
                f"Set {prefix}_BASE_URL and {prefix}_MODEL "
                f"(and {prefix}_API_KEY if the endpoint needs one)."
            )
        key = os.environ.get(f"{prefix}_API_KEY", "")
        headers = {"Authorization": f"Bearer {key}"} if key else {}
        self._http = httpx.Client(headers=headers, timeout=timeout)
        self._httpx = httpx
        self._url = f"{base_url}/chat/completions"
        self.retries = retries
        self.name = self.model
        # Extra request fields, e.g. {"reasoning_effort": "none"} to stop a
        # Gemma 4 behind Ollama from thinking before it answers.
        self.extra = dict(extra or {})

    def chat(
        self, messages: list[dict], temperature: float = 0.0, max_tokens: int = 512
    ) -> str:
        """The assistant message's `content`; reasoning fields are not read."""
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            **self.extra,
        }
        problem = "no attempt"
        for attempt in range(self.retries):
            if attempt:
                time.sleep(2**attempt)
            try:
                response = self._http.post(self._url, json=payload)
            except self._httpx.HTTPError as error:
                problem = type(error).__name__
                continue
            if response.status_code == 429 or response.status_code >= 500:
                problem = f"HTTP {response.status_code}"
                continue
            if response.status_code != 200:
                raise EndpointError(f"endpoint answered HTTP {response.status_code}")
            try:
                return response.json()["choices"][0]["message"].get("content") or ""
            except (ValueError, KeyError, IndexError, TypeError):
                problem = "unexpected response body"
        raise EndpointError(f"request failed after {self.retries} tries: {problem}")
