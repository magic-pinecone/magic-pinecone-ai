"""Mirror the NCU club list (club.adm.ncu.edu.tw) into the `clubs` table."""

import logging
import re
from datetime import UTC, datetime
from typing import Any

import httpx
from sqlmodel import Session, select

from app.core.celery_app import celery_app
from app.core.db import engine
from app.models.club import Club
from app.services.course_detail import HEADERS

logger = logging.getLogger(__name__)

CLUB_SITE = "https://club.adm.ncu.edu.tw"
# The tab codes of https://club.adm.ncu.edu.tw/clubs, in the site's order.
CLUB_CATEGORIES = {
    "A": "聯誼性",
    "B": "學術性",
    "C": "康樂性",
    "D": "系學會",
    "E": "體育性",
    "F": "服務性",
    "G": "學生自治組織",
}

_BR = re.compile(r"<br\s*/?>", re.IGNORECASE)


def _text(value: Any) -> str:
    if not value:
        return ""
    # NCU indents the lines after each <br> with spaces.
    return "\n".join(line.strip() for line in _BR.split(str(value))).strip()


def parse_club(raw: dict[str, Any]) -> dict[str, Any]:
    """Map one club_list row to Club columns."""
    picture = _text(raw.get("club_pic_path"))
    return {
        "club_id": int(raw["clubID"]),
        "category": raw["type"],
        "sort_order": int(raw.get("SortByType") or 0),
        "name": _text(raw.get("club_name")),
        "name_en": _text(raw.get("club_eName")),
        "description": _text(raw.get("description")),
        "description_en": _text(raw.get("description_en")),
        "address": _text(raw.get("address")),
        "website": _text(raw.get("web_site")),
        "picture_url": f"{CLUB_SITE}/images/club/{picture}" if picture else "",
    }


def fetch_clubs(timeout: float = 15.0) -> list[dict[str, Any]]:
    """Fetch every category. Raises httpx.HTTPError when any category fails."""
    clubs: list[dict[str, Any]] = []
    with httpx.Client(headers=HEADERS, timeout=timeout) as client:
        for category in CLUB_CATEGORIES:
            resp = client.get(f"{CLUB_SITE}/api/club_list/{category}")
            resp.raise_for_status()
            clubs.extend(parse_club(row) for row in resp.json())
    return clubs


def save_clubs(session: Session, clubs: list[dict[str, Any]]) -> None:
    """Make the table match `clubs` exactly: upsert them, delete the rest."""
    if not clubs:
        # An empty answer means NCU is broken, not that every club closed.
        raise ValueError("Refusing to replace the club table with an empty list.")
    now = datetime.now(UTC)
    by_id = {c["club_id"]: c for c in clubs}
    for existing in session.exec(select(Club)).all():
        data = by_id.pop(existing.club_id, None)
        if data is None:
            session.delete(existing)
            continue
        for field, value in data.items():
            setattr(existing, field, value)
        existing.updated_at = now
    for data in by_id.values():
        session.add(Club(**data, updated_at=now))
    session.commit()


@celery_app.task(name="tasks.club_scraper.sync_ncu_clubs")
def sync_ncu_clubs() -> dict[str, Any]:
    """Celery task: fetch the NCU club list and mirror it into the database."""
    clubs = fetch_clubs()
    with Session(engine) as session:
        save_clubs(session, clubs)
    logger.info(f"Synced {len(clubs)} clubs from NCU.")
    return {"status": "SUCCESS", "club_count": len(clubs)}


def queue_first_club_sync(session: Session) -> None:
    """Queue a sync when the clubs table is empty, so a fresh deployment has clubs without waiting for beat.

    Beat runs the daily sync only where ENABLE_BEAT is on, which previews leave off.
    """
    if session.exec(select(Club).limit(1)).first() is not None:
        return
    try:
        sync_ncu_clubs.delay()
        logger.info("Clubs table is empty; queued a club sync")
    except Exception as e:  # noqa: BLE001 - a missing broker must not stop the API from starting
        logger.warning("Could not queue the first club sync: %s", e)
