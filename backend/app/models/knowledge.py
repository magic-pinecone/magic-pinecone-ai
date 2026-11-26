from datetime import UTC, datetime

from sqlmodel import Column, Field, LargeBinary, SQLModel


class KnowledgeEmbedding(SQLModel, table=True):
    __tablename__ = "knowledge_embeddings"

    content_hash: str = Field(primary_key=True)
    model: str = Field(index=True)
    dimensions: int
    vector: bytes = Field(sa_column=Column(LargeBinary, nullable=False))
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
