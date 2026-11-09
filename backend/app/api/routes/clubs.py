from datetime import datetime
from typing import Any

from fastapi import APIRouter, Query
from pydantic import BaseModel
from sqlmodel import select

from app.api.deps import SessionDep
from app.models.club import Club
from app.tasks.club_scraper import CLUB_CATEGORIES, sync_ncu_clubs

router = APIRouter(prefix="/clubs", tags=["clubs"])


class ClubResponse(BaseModel):
    club_id: int
    category: str
    category_name: str
    sort_order: int
    name: str
    name_en: str
    description: str
    description_en: str
    address: str
    website: str
    picture_url: str
    updated_at: datetime


@router.get("", response_model=list[ClubResponse])
def list_clubs(
    session: SessionDep,
    category: str | None = Query(None, description="NCU category code, A to G"),
) -> list[ClubResponse]:
    """List clubs in the order the NCU club site shows them."""
    statement = select(Club).order_by(Club.category, Club.sort_order)  # type: ignore[arg-type]
    if category:
        statement = statement.where(Club.category == category)
    return [
        ClubResponse(
            **club.model_dump(), category_name=CLUB_CATEGORIES.get(club.category, "")
        )
        for club in session.exec(statement).all()
    ]


@router.post("/sync", response_model=dict[str, Any])
def trigger_club_sync() -> Any:
    """Sync clubs from NCU in the Celery worker; poll GET /scraper/status/{task_id}."""
    task = sync_ncu_clubs.delay()
    return {
        "message": "Club sync task triggered successfully",
        "task_id": task.id,
        "status": task.status,
    }
