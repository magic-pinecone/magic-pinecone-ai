from datetime import UTC, datetime

from sqlmodel import Field, SQLModel


class Club(SQLModel, table=True):
    """A student club, mirrored from the NCU club site's club_list API."""

    __tablename__ = "clubs"

    club_id: int = Field(primary_key=True)
    # NCU's tab code, A to G; see app.tasks.club_scraper.CLUB_CATEGORIES.
    category: str = Field(index=True)
    sort_order: int = 0
    name: str
    name_en: str = ""
    description: str = ""
    description_en: str = ""
    address: str = ""
    website: str = ""
    picture_url: str = ""
    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
