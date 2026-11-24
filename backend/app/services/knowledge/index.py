import hashlib
import logging
import threading
from collections import Counter
from collections.abc import Iterable, Sequence
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import numpy as np
from sqlmodel import Session, col, select

from app.core.config import settings
from app.models.knowledge import KnowledgeEmbedding
from app.services import student_services
from app.services.knowledge import lexical
from app.services.knowledge.embedder import (
    EmbeddingClient,
    EmbeddingError,
    configured_embedder,
    document_input,
    query_input,
)
from app.services.knowledge.sources import (
    SOURCE_TYPES,
    Document,
    database_documents,
    file_documents,
)

logger = logging.getLogger(__name__)

PASSAGE_LINES = student_services.PASSAGE_LINES
PASSAGE_STEP = student_services.PASSAGE_STEP
CANDIDATES = 20
RRF_K = 60
LOOKUP_BATCH = 500

_UNSET: Any = object()


@dataclass
class Corpus:
    documents: tuple[Document, ...]
    lexical: lexical.LexicalIndex
    inputs: tuple[str, ...]
    by_id: dict[str, Document]
    built_at: datetime
    with_database: bool
    model: str | None = None
    hashes: tuple[str, ...] = ()
    vectors: np.ndarray | None = None
    has_vector: np.ndarray | None = None
    errors: list[str] = field(default_factory=list)


def chunk_documents(documents: Iterable[Document]) -> list[lexical.Chunk]:
    return [
        lexical.make_chunk(document, start, text)
        for document in documents
        for start, text in lexical.windows(document.lines, PASSAGE_LINES, PASSAGE_STEP)
    ]


def build(documents: Sequence[Document], with_database: bool = False) -> Corpus:
    chunks = chunk_documents(documents)
    return Corpus(
        documents=tuple(documents),
        lexical=lexical.LexicalIndex(chunks),
        inputs=tuple(document_input(c.document.title, c.text) for c in chunks),
        by_id={document.id: document for document in documents},
        built_at=datetime.now(UTC),
        with_database=with_database,
    )


def content_hash(model: str, text: str) -> str:
    return hashlib.sha256(f"{model}\n{text}".encode()).hexdigest()


def _stored(session: Session, hashes: Sequence[str]) -> dict[str, bytes]:
    found: dict[str, bytes] = {}
    wanted = sorted(set(hashes))
    for start in range(0, len(wanted), LOOKUP_BATCH):
        rows = session.exec(
            select(KnowledgeEmbedding).where(
                col(KnowledgeEmbedding.content_hash).in_(
                    wanted[start : start + LOOKUP_BATCH]
                )
            )
        )
        found.update({row.content_hash: row.vector for row in rows})
    return found


def attach_vectors(
    corpus: Corpus,
    session: Session,
    embedder: EmbeddingClient,
    embed_missing: bool = False,
) -> int:
    model = embedder.name
    hashes = tuple(content_hash(model, text) for text in corpus.inputs)
    stored = _stored(session, hashes)
    created = 0
    if embed_missing:
        first: dict[str, int] = {}
        for i, digest in enumerate(hashes):
            if digest not in stored:
                first.setdefault(digest, i)
        missing = list(first.values())
        step = embedder.batch_size * 8
        for start in range(0, len(missing), step):
            batch = missing[start : start + step]
            try:
                vectors = embedder.embed([corpus.inputs[i] for i in batch])
            except EmbeddingError as error:
                corpus.errors.append(f"embedding endpoint: {error}")
                break
            for i, vector in zip(batch, vectors, strict=True):
                data = vector.astype(np.float32).tobytes()
                session.merge(
                    KnowledgeEmbedding(
                        content_hash=hashes[i],
                        model=model,
                        dimensions=int(vector.shape[0]),
                        vector=data,
                    )
                )
                stored[hashes[i]] = data
                created += 1
            session.commit()
    dimensions = next((len(data) // 4 for data in stored.values()), 0)
    if dimensions:
        matrix = np.zeros((len(hashes), dimensions), dtype=np.float32)
        present = np.zeros(len(hashes), dtype=bool)
        for i, digest in enumerate(hashes):
            data = stored.get(digest)
            if data is not None and len(data) == dimensions * 4:
                matrix[i] = np.frombuffer(data, dtype=np.float32)
                present[i] = True
        corpus.vectors, corpus.has_vector = matrix, present
    corpus.model, corpus.hashes = model, hashes
    return created


_lock = threading.Lock()
REINDEX_LOCK = threading.Lock()
_current: Corpus | None = None


def corpus() -> Corpus:
    global _current
    if _current is None:
        with _lock:
            if _current is None:
                _current = build(file_documents())
    return _current


def refresh(
    session: Session | None = None,
    embedder: EmbeddingClient | None = _UNSET,
    embed_missing: bool = False,
) -> dict[str, Any]:
    global _current
    embedder = configured_embedder() if embedder is _UNSET else embedder
    documents = list(file_documents())
    if session is not None:
        documents += database_documents(session)
    fresh = build(documents, with_database=session is not None)
    created = 0
    if session is not None and embedder is not None:
        created = attach_vectors(fresh, session, embedder, embed_missing=embed_missing)
    with _lock:
        _current = fresh
    return {**stats(fresh), "embedded": created}


def load_documents() -> tuple[Document, ...]:
    return corpus().documents


def get_document(document_id: str) -> Document | None:
    return corpus().by_id.get(document_id)


def stats(current: Corpus | None = None) -> dict[str, Any]:
    current = current or corpus()
    documents = Counter(d.source_type for d in current.documents)
    passages = Counter(c.document.source_type for c in current.lexical.chunks)
    embedded: Counter = Counter()
    if current.has_vector is not None:
        for chunk, present in zip(
            current.lexical.chunks, current.has_vector, strict=True
        ):
            if present:
                embedded[chunk.document.source_type] += 1
    latest: dict[str, str] = {}
    for document in current.documents:
        if document.fetched_on > latest.get(document.source_type, ""):
            latest[document.source_type] = document.fetched_on
    return {
        "built_at": current.built_at,
        "with_database": current.with_database,
        "embedding_model": current.model,
        "errors": list(current.errors),
        "sources": [
            {
                "source_type": source_type,
                "documents": documents.get(source_type, 0),
                "passages": passages.get(source_type, 0),
                "embedded_passages": embedded.get(source_type, 0),
                "latest_fetched_on": latest.get(source_type) or None,
            }
            for source_type in SOURCE_TYPES
        ],
    }


def _vector_ranking(
    current: Corpus,
    query: str,
    keep: np.ndarray,
    embedder: EmbeddingClient,
) -> list[tuple[int, float]]:
    if (
        current.vectors is None
        or current.has_vector is None
        or embedder.name != current.model
    ):
        return []
    try:
        vector = embedder.embed([query_input(query)])[0]
    except EmbeddingError as error:
        logger.warning("query embedding failed: %s", error)
        return []
    similarities = current.vectors @ vector
    usable = (
        keep & current.has_vector & (similarities >= settings.KNOWLEDGE_MIN_SIMILARITY)
    )
    candidates = np.flatnonzero(usable)
    best = candidates[np.argsort(-similarities[candidates], kind="stable")][:CANDIDATES]
    return [(int(i), float(similarities[i])) for i in best]


def search(
    query: str,
    source_types: Sequence[str] | None = None,
    limit: int = 5,
    embedder: EmbeddingClient | None = _UNSET,
) -> list[dict[str, Any]]:
    current = corpus()
    embedder = configured_embedder() if embedder is _UNSET else embedder
    wanted = set(source_types or ())
    chunks = current.lexical.chunks
    position = {id(chunk): i for i, chunk in enumerate(chunks)}
    keep_chunk = (
        (lambda chunk: chunk.document.source_type in wanted) if wanted else None
    )

    words = lexical.words(" ".join([query, *student_services.english_terms(query)]))
    lexical_hits = (
        current.lexical.score(words, keep_chunk)[:CANDIDATES] if words else []
    )
    matched = {position[id(chunk)]: found for _, chunk, found in lexical_hits}

    vector_hits: list[tuple[int, float]] = []
    if embedder is not None and query.strip():
        keep = np.array(
            [not wanted or chunk.document.source_type in wanted for chunk in chunks],
            dtype=bool,
        )
        vector_hits = _vector_ranking(current, query, keep, embedder)
    similarity = dict(vector_hits)

    fused: dict[int, float] = {}
    for rank, (_, chunk, _) in enumerate(lexical_hits):
        i = position[id(chunk)]
        fused[i] = fused.get(i, 0.0) + 1 / (RRF_K + rank + 1)
    for rank, (i, _) in enumerate(vector_hits):
        fused[i] = fused.get(i, 0.0) + 1 / (RRF_K + rank + 1)
    order = sorted(fused, key=lambda i: (-fused[i], i))
    chosen = lexical.without_overlap((chunks[i] for i in order), limit, PASSAGE_LINES)

    results = []
    for chunk in chosen:
        i = position[id(chunk)]
        document = chunk.document
        retrieval = (
            "both"
            if i in matched and i in similarity
            else "keyword"
            if i in matched
            else "vector"
        )
        results.append(
            {
                "id": document.id,
                "title": document.title,
                "topic": document.topic,
                "source_type": document.source_type,
                "lang": document.lang,
                "url": document.url,
                "fetched_on": document.fetched_on,
                "matched_words": matched.get(i, []),
                "similarity": round(similarity[i], 4) if i in similarity else None,
                "score": round(fused[i], 6),
                "retrieval": retrieval,
                "passage": chunk.text,
            }
        )
    return results


def refresh_from_database(embed_missing: bool = True) -> dict[str, Any]:
    from app.core.db import engine

    with Session(engine) as session:
        return refresh(session, embed_missing=embed_missing)
