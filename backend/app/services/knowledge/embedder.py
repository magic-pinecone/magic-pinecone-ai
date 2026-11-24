from collections.abc import Sequence
from functools import cache

import httpx
import numpy as np

from app.core.config import settings

QUERY_PREFIX = "task: search result | query: "


class EmbeddingError(RuntimeError):
    pass


def document_input(title: str, text: str) -> str:
    return f"title: {title or 'none'} | text: {text}"


def query_input(query: str) -> str:
    return QUERY_PREFIX + query


class EmbeddingClient:
    def __init__(
        self,
        base_url: str,
        model: str,
        api_key: str | None = None,
        timeout: float = 30.0,
        batch_size: int = 64,
        transport: httpx.BaseTransport | None = None,
    ):
        self.name = model
        self.batch_size = max(1, batch_size)
        self._url = base_url.rstrip("/") + "/embeddings"
        headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._http = httpx.Client(headers=headers, timeout=timeout, transport=transport)

    def _batch(self, texts: Sequence[str]) -> list[list[float]]:
        try:
            response = self._http.post(
                self._url, json={"model": self.name, "input": list(texts)}
            )
        except (httpx.HTTPError, httpx.InvalidURL) as error:
            raise EmbeddingError(type(error).__name__) from error
        if response.status_code != 200:
            raise EmbeddingError(f"HTTP {response.status_code}")
        try:
            rows = sorted(response.json()["data"], key=lambda row: row["index"])
            vectors = [row["embedding"] for row in rows]
        except (ValueError, KeyError, TypeError) as error:
            raise EmbeddingError("unexpected response body") from error
        if len(vectors) != len(texts):
            raise EmbeddingError("unexpected response body")
        return vectors

    def embed(self, texts: Sequence[str]) -> np.ndarray:
        rows: list[list[float]] = []
        for start in range(0, len(texts), self.batch_size):
            rows += self._batch(texts[start : start + self.batch_size])
        matrix = np.asarray(rows, dtype=np.float32).reshape(len(rows), -1)
        norms = np.linalg.norm(matrix, axis=1, keepdims=True)
        return matrix / np.where(norms == 0, 1, norms)


@cache
def configured_embedder() -> EmbeddingClient | None:
    if not settings.EMBEDDING_BASE_URL or not settings.EMBEDDING_MODEL:
        return None
    return EmbeddingClient(
        settings.EMBEDDING_BASE_URL,
        settings.EMBEDDING_MODEL,
        settings.EMBEDDING_API_KEY,
        timeout=settings.EMBEDDING_TIMEOUT_SECONDS,
        batch_size=settings.EMBEDDING_BATCH_SIZE,
    )
