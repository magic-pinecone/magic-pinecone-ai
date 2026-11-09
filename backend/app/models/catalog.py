from datetime import UTC, datetime

from sqlmodel import JSON, Column, Field, SQLModel

CATALOG_STATE_ID = 1


class CatalogState(SQLModel, table=True):
    """Single-row record of which semester the `courses` table currently holds."""

    __tablename__ = "catalog_state"

    id: int = Field(default=CATALOG_STATE_ID, primary_key=True)
    # "<academic year>-<term>", e.g. "115-1".
    semester: str
    last_synced_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class CourseDetail(SQLModel, table=True):
    """Syllabus of one course, cached from the NCU course detail page."""

    __tablename__ = "course_details"

    serial_no: int = Field(primary_key=True)
    objectives: str = ""
    content: str = ""
    books: str = ""
    teaching_method: str = ""
    grading_policy: str = ""
    distribution_conditions: list[dict] = Field(default=[], sa_column=Column(JSON))
    fetched_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
