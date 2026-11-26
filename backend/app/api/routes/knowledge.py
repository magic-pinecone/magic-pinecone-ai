from datetime import datetime
from typing import Annotated, Literal

from fastapi import APIRouter, BackgroundTasks, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.deps import SessionDep
from app.services.knowledge import index as knowledge_index
from app.services.knowledge.sources import SOURCE_TYPES

router = APIRouter(prefix="/knowledge", tags=["knowledge"])

SourceType = Literal[
    "oia_page",
    "gov_guide",
    "graduation_language",
    "intl_program",
    "credits",
    "service_learning",
    "contact",
    "place",
    "destination",
    "food_place",
    "academic_calendar",
    "course",
    "club",
]
assert set(SourceType.__args__) == set(SOURCE_TYPES)


class KnowledgePassage(BaseModel):
    id: str = Field(description="Id of the document the passage belongs to")
    title: str
    topic: str
    source_type: SourceType
    lang: str = Field(description="Language of the document, empty when unknown")
    url: str = Field(description="Official page the passage was read from")
    fetched_on: str = Field(
        description="Day the source was read or checked, ISO date or empty"
    )
    matched_words: list[str] = Field(
        description="Query words found by the keyword search"
    )
    similarity: float | None = Field(
        description="Cosine similarity to the query; null when the passage was not found by its embedding"
    )
    score: float = Field(description="Reciprocal-rank-fusion score; higher is better")
    retrieval: Literal["keyword", "vector", "both"]
    passage: str


class KnowledgeSearchResponse(BaseModel):
    query: str
    source_types: list[SourceType]
    passages: list[KnowledgePassage]


class KnowledgeDocument(BaseModel):
    id: str
    title: str
    topic: str
    source_type: SourceType
    lang: str
    url: str
    fetched_on: str
    text: str


class SourceStats(BaseModel):
    source_type: SourceType
    documents: int
    passages: int
    embedded_passages: int
    latest_fetched_on: str | None


class KnowledgeSources(BaseModel):
    built_at: datetime
    with_database: bool = Field(
        description="Whether courses and clubs from the database are in the index"
    )
    embedding_model: str | None = Field(
        description="Model of the stored embeddings; null when the search is keyword-only"
    )
    errors: list[str]
    sources: list[SourceStats]


class ReindexResponse(KnowledgeSources):
    embedded: int = Field(description="Passages embedded by this run")


class ReindexStarted(BaseModel):
    status: Literal["started"]


@router.get("/search", response_model=KnowledgeSearchResponse)
def search_knowledge(
    q: Annotated[
        str,
        Query(
            min_length=1,
            max_length=500,
            description="Question or keywords, any language",
        ),
    ],
    source_types: Annotated[
        list[SourceType] | None,
        Query(description="Only these kinds of sources; all when empty"),
    ] = None,
    limit: Annotated[int, Query(ge=1, le=20)] = 5,
) -> KnowledgeSearchResponse:
    """Hybrid keyword and embedding search over the OIA website and all backend data.

    Passages come best first. An empty list means nothing in the index matches.
    """
    source_types = source_types or []
    passages = knowledge_index.search(q, source_types=source_types, limit=limit)
    return KnowledgeSearchResponse(
        query=q, source_types=source_types, passages=passages
    )


@router.get("/documents/{document_id}", response_model=KnowledgeDocument)
def get_knowledge_document(document_id: str) -> KnowledgeDocument:
    """Full text of one indexed document."""
    document = knowledge_index.get_document(document_id)
    if document is None:
        raise HTTPException(status_code=404, detail="Document not found")
    return KnowledgeDocument(
        id=document.id,
        title=document.title,
        topic=document.topic,
        source_type=document.source_type,
        lang=document.lang,
        url=document.url,
        fetched_on=document.fetched_on,
        text="\n".join(document.lines),
    )


@router.get("/sources", response_model=KnowledgeSources)
def list_knowledge_sources() -> KnowledgeSources:
    """Documents, passages and embedded passages per kind of source."""
    return KnowledgeSources(**knowledge_index.stats())


def _reindex_in_background() -> None:
    try:
        knowledge_index.refresh_from_database()
    finally:
        knowledge_index.REINDEX_LOCK.release()


@router.post(
    "/reindex",
    response_model=ReindexResponse | ReindexStarted,
    responses={409: {"description": "A reindex is already running"}},
)
def reindex_knowledge(
    session: SessionDep,
    background_tasks: BackgroundTasks,
    wait: Annotated[
        bool, Query(description="Run in the request and return the new stats")
    ] = False,
) -> ReindexResponse | ReindexStarted:
    """Reload courses and clubs from the database and embed passages that have no embedding yet."""
    if not knowledge_index.REINDEX_LOCK.acquire(blocking=False):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="Reindex already running"
        )
    if not wait:
        background_tasks.add_task(_reindex_in_background)
        return ReindexStarted(status="started")
    try:
        return ReindexResponse(**knowledge_index.refresh(session, embed_missing=True))
    finally:
        knowledge_index.REINDEX_LOCK.release()
