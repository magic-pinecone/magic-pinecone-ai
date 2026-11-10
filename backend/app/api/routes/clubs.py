from datetime import datetime
from typing import Any

from fastapi import APIRouter, Query
from pydantic import BaseModel
from sqlmodel import select

from app.api.deps import SessionDep
from app.models.club import Club
from app.services.translator import translate
from app.tasks.club_scraper import CLUB_CATEGORIES, sync_ncu_clubs

router = APIRouter(prefix="/clubs", tags=["clubs"])

# The NCU club site writes in Traditional Chinese, so these need no translation.
SOURCE_LANGS = {"zh", "zh-tw", "zh-hant", "zh-hant-tw"}
TRANSLATED = ("name", "description", "address", "category_name")
# Hand-written, since machine translation renders 聯誼性 as "sociability".
CATEGORY_NAMES_EN = {
    "A": "Social",
    "B": "Academic",
    "C": "Recreational",
    "D": "Departmental",
    "E": "Sports",
    "F": "Service",
    "G": "Student Government",
}


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
    lang: str | None = Query(
        None,
        pattern=r"^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$",
        description="Language tag such as ja or fr; translates name, description, "
        "address and category_name. Text that fails to translate stays in Chinese.",
    ),
) -> list[ClubResponse]:
    """List clubs in the order the NCU club site shows them."""
    statement = select(Club).order_by(Club.category, Club.sort_order)  # type: ignore[arg-type]
    if category:
        statement = statement.where(Club.category == category)
    clubs = [
        ClubResponse(
            **club.model_dump(), category_name=CLUB_CATEGORIES.get(club.category, "")
        )
        for club in session.exec(statement).all()
    ]
    if not lang or lang.lower() in SOURCE_LANGS:
        return clubs
    texts = translate(session, (getattr(c, f) for c in clubs for f in TRANSLATED), lang)
    english = lang.lower().split("-")[0] == "en"
    return [
        c.model_copy(
            update={f: texts.get(getattr(c, f), getattr(c, f)) for f in TRANSLATED}
            # The club's own English beats a machine translation.
            | ({"name": c.name_en} if english and c.name_en else {})
            | (
                {"category_name": CATEGORY_NAMES_EN[c.category]}
                if english and c.category in CATEGORY_NAMES_EN
                else {}
            )
            | (
                {"description": c.description_en}
                if english and c.description_en
                else {}
            )
        )
        for c in clubs
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
