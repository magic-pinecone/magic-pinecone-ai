from datetime import UTC, datetime
from typing import Literal

from sqlmodel import JSON, Column, Field, SQLModel

ANNOUNCEMENT_STATE_ID = 1

AnnouncementScope = Literal["AFFILIATION", "DEPARTMENT"]
AffiliationType = Literal[
    "ALL", "UNDERGRADUATE", "GRADUATE", "INTERNATIONAL", "FACULTY"
]


class AnnouncementSyncState(SQLModel, table=True):
    """Single-row record tracking when announcements were last synced."""

    __tablename__ = "announcement_sync_state"

    id: int = Field(default=ANNOUNCEMENT_STATE_ID, primary_key=True)
    last_synced_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class Announcement(SQLModel, table=True):
    """Campus affiliation (school/office/scholarship) or department announcement."""

    __tablename__ = "announcements"

    announcement_id: str = Field(primary_key=True)
    scope: str = Field(index=True)  # "AFFILIATION" | "DEPARTMENT"
    source_id: str = Field(index=True)
    category: str = Field(index=True)
    unit_name: str = Field(default="", index=True)

    college_id: str | None = Field(default=None, index=True)
    college_name: str = Field(default="", index=True)
    department_id: str | None = Field(default=None, index=True)
    department_name: str = Field(default="", index=True)

    affiliations: list[str] = Field(default=[], sa_column=Column(JSON))

    title: str
    summary: dict[str, str] = Field(default={}, sa_column=Column(JSON))
    published_date: str | None = Field(default=None, index=True)
    url: str
    attachment_url: str | None = None
    is_pinned: bool = False

    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
