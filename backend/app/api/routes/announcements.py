"""Announcement endpoints for NCU Affiliation and Department announcements."""

from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from sqlmodel import select

from app.api.deps import SessionDep
from app.models.announcement import (
    ANNOUNCEMENT_STATE_ID,
    AffiliationType,
    Announcement,
    AnnouncementScope,
    AnnouncementSyncState,
)
from app.models.course import Department
from app.services.announcements import DEPARTMENT_SOURCES
from app.tasks.announcement_scraper import (
    canonical_department_names,
    match_department,
)

router = APIRouter(prefix="/announcements", tags=["announcements"])

_VALID_AFFILIATIONS = {"ALL", "UNDERGRADUATE", "GRADUATE", "INTERNATIONAL", "FACULTY"}


class AnnouncementItem(BaseModel):
    announcement_id: str
    scope: AnnouncementScope
    source_id: str
    category: str
    unit_name: str
    college_id: str | None
    college_name: str
    department_id: str | None
    department_name: str
    affiliations: list[AffiliationType]
    title: str
    summary: dict[str, str]
    published_date: str | None
    url: str
    attachment_url: str | None
    is_pinned: bool


class AnnouncementStatus(BaseModel):
    last_updated: datetime
    total_count: int
    affiliation_count: int
    department_count: int


class AnnouncementListResponse(BaseModel):
    last_updated: datetime
    total: int
    offset: int
    limit: int
    announcements: list[AnnouncementItem]


class DepartmentAnnouncementSource(BaseModel):
    source_id: str
    college_name: str
    department_id: str | None
    department_name: str
    announcement_count: int


def _as_utc(value: datetime) -> datetime:
    return value if value.tzinfo else value.replace(tzinfo=UTC)


def _require_synced(session: SessionDep) -> AnnouncementSyncState:
    state = session.get(AnnouncementSyncState, ANNOUNCEMENT_STATE_ID)
    if state is None:
        raise HTTPException(
            status_code=503,
            detail="Announcements have not been synced yet. "
            "Trigger POST /scraper/announcements/trigger and retry.",
        )
    return state


def _to_item(row: Announcement) -> AnnouncementItem:
    affiliations: list[AffiliationType] = [
        a for a in (row.affiliations or []) if a in _VALID_AFFILIATIONS
    ] or ["ALL"]
    scope: AnnouncementScope = (
        "DEPARTMENT" if row.scope == "DEPARTMENT" else "AFFILIATION"
    )
    return AnnouncementItem(
        announcement_id=row.announcement_id,
        scope=scope,
        source_id=row.source_id,
        category=row.category,
        unit_name=row.unit_name,
        college_id=row.college_id,
        college_name=row.college_name or "",
        department_id=row.department_id,
        department_name=row.department_name or "",
        affiliations=affiliations,
        title=row.title,
        summary=row.summary or {},
        published_date=row.published_date,
        url=row.url,
        attachment_url=row.attachment_url,
        is_pinned=bool(row.is_pinned),
    )


def _filter_and_paginate(
    rows: list[Announcement],
    *,
    scope: AnnouncementScope | None = None,
    affiliation: AffiliationType | None = None,
    department_id: str | None = None,
    department_name: str | None = None,
    college_name: str | None = None,
    category: str | None = None,
    search: str | None = None,
    offset: int = 0,
    limit: int = 100,
) -> tuple[int, list[AnnouncementItem]]:
    dept_candidates = (
        canonical_department_names(department_name) if department_name else set()
    )
    search_lower = search.strip().lower() if search else ""

    filtered: list[Announcement] = []
    for row in rows:
        if scope and row.scope != scope:
            continue
        if category and row.category != category:
            continue
        if affiliation and affiliation != "ALL":
            row_affs = set(row.affiliations or ["ALL"])
            if affiliation not in row_affs and "ALL" not in row_affs:
                continue
        if department_id and row.department_id != department_id:
            continue
        if dept_candidates:
            row_dept = (row.department_name or "").strip()
            if not row_dept or not (
                row_dept in dept_candidates
                or any(c in row_dept or row_dept in c for c in dept_candidates)
            ):
                continue
        if college_name and college_name.strip() not in (row.college_name or ""):
            continue
        if search_lower:
            haystack = " ".join(
                [
                    row.title or "",
                    row.unit_name or "",
                    row.category or "",
                    " ".join((row.summary or {}).values()),
                ]
            ).lower()
            if search_lower not in haystack:
                continue

        filtered.append(row)

    # Sort: pinned first, then newest published_date first, then announcement_id
    filtered.sort(
        key=lambda r: (
            0 if r.is_pinned else 1,
            0 if r.published_date else 1,
            _invert_date_key(r.published_date) if r.published_date else "",
            r.announcement_id,
        )
    )

    total = len(filtered)
    sliced = filtered[offset : offset + limit]
    return total, [_to_item(r) for r in sliced]


def _invert_date_key(date_str: str) -> str:
    """Invert digits so descending YYYY-MM-DD sorts ascending lexicographically."""
    return "".join(
        chr(ord("9") - int(ch) + ord("0")) if ch.isdigit() else ch for ch in date_str
    )


@router.get("/status", response_model=AnnouncementStatus)
def get_announcement_status(session: SessionDep) -> AnnouncementStatus:
    """Report when announcements were last synced and counts by scope."""
    state = _require_synced(session)
    rows = session.exec(select(Announcement)).all()
    affiliation_count = sum(1 for r in rows if r.scope == "AFFILIATION")
    department_count = sum(1 for r in rows if r.scope == "DEPARTMENT")
    return AnnouncementStatus(
        last_updated=_as_utc(state.last_synced_at),
        total_count=len(rows),
        affiliation_count=affiliation_count,
        department_count=department_count,
    )


@router.get("", response_model=AnnouncementListResponse)
def list_announcements(
    session: SessionDep,
    scope: Annotated[
        AnnouncementScope | None,
        Query(description="Filter by scope (AFFILIATION or DEPARTMENT)"),
    ] = None,
    affiliation: Annotated[
        AffiliationType | None,
        Query(
            description="Filter by target affiliation (ALL, UNDERGRADUATE, GRADUATE, INTERNATIONAL, FACULTY)"
        ),
    ] = None,
    department_id: str | None = Query(None, description="Filter by department ID"),
    department_name: str | None = Query(
        None, description="Filter by department name (full or abbreviated)"
    ),
    college_name: str | None = Query(None, description="Filter by college name"),
    category: str | None = Query(None, description="Filter by announcement category"),
    search: str | None = Query(
        None, description="Search keyword in title, unit_name, or summary"
    ),
    offset: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
) -> AnnouncementListResponse:
    """List and filter both Affiliation and Department announcements."""
    state = _require_synced(session)
    rows = list(session.exec(select(Announcement)).all())
    total, items = _filter_and_paginate(
        rows,
        scope=scope,
        affiliation=affiliation,
        department_id=department_id,
        department_name=department_name,
        college_name=college_name,
        category=category,
        search=search,
        offset=offset,
        limit=limit,
    )
    return AnnouncementListResponse(
        last_updated=_as_utc(state.last_synced_at),
        total=total,
        offset=offset,
        limit=limit,
        announcements=items,
    )


@router.get("/affiliation", response_model=AnnouncementListResponse)
def list_affiliation_announcements(
    session: SessionDep,
    affiliation: Annotated[
        AffiliationType | None,
        Query(
            description="Target affiliation (ALL, UNDERGRADUATE, GRADUATE, INTERNATIONAL, FACULTY)"
        ),
    ] = None,
    category: str | None = Query(
        None, description="Filter by category (e.g. 獎學金, 招募資訊, 教務, 學務)"
    ),
    search: str | None = Query(
        None, description="Search keyword in title, unit_name, or summary"
    ),
    offset: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
) -> AnnouncementListResponse:
    """List Affiliation Announcements (scholarships, part-time jobs, and campus offices)."""
    state = _require_synced(session)
    rows = list(
        session.exec(
            select(Announcement).where(Announcement.scope == "AFFILIATION")
        ).all()
    )
    total, items = _filter_and_paginate(
        rows,
        scope="AFFILIATION",
        affiliation=affiliation,
        category=category,
        search=search,
        offset=offset,
        limit=limit,
    )
    return AnnouncementListResponse(
        last_updated=_as_utc(state.last_synced_at),
        total=total,
        offset=offset,
        limit=limit,
        announcements=items,
    )


@router.get("/department", response_model=AnnouncementListResponse)
def list_department_announcements(
    session: SessionDep,
    department_id: str | None = Query(None, description="Filter by department ID"),
    department_name: str | None = Query(
        None, description="Filter by department name (e.g. 資訊工程學系 or 資工系)"
    ),
    college_name: str | None = Query(None, description="Filter by college name"),
    category: str | None = Query(None, description="Filter by category"),
    search: str | None = Query(
        None, description="Search keyword in title, unit_name, or summary"
    ),
    offset: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
) -> AnnouncementListResponse:
    """List Department Announcements filtered by department or college."""
    state = _require_synced(session)
    rows = list(
        session.exec(
            select(Announcement).where(Announcement.scope == "DEPARTMENT")
        ).all()
    )
    total, items = _filter_and_paginate(
        rows,
        scope="DEPARTMENT",
        department_id=department_id,
        department_name=department_name,
        college_name=college_name,
        category=category,
        search=search,
        offset=offset,
        limit=limit,
    )
    return AnnouncementListResponse(
        last_updated=_as_utc(state.last_synced_at),
        total=total,
        offset=offset,
        limit=limit,
        announcements=items,
    )


@router.get("/departments", response_model=list[DepartmentAnnouncementSource])
def list_department_announcement_sources(
    session: SessionDep,
) -> list[DepartmentAnnouncementSource]:
    """List supported departments for Department Announcements and their item counts."""
    departments = list(session.exec(select(Department)).all())
    dept_rows = session.exec(
        select(Announcement).where(Announcement.scope == "DEPARTMENT")
    ).all()
    counts_by_source: dict[str, int] = {}
    for row in dept_rows:
        counts_by_source[row.source_id] = counts_by_source.get(row.source_id, 0) + 1

    sources: list[DepartmentAnnouncementSource] = []
    for src in DEPARTMENT_SOURCES:
        matched_dept = match_department(src.department_name, departments)
        sources.append(
            DepartmentAnnouncementSource(
                source_id=src.source_id,
                college_name=src.college_name,
                department_id=src.department_id
                or (matched_dept.department_id if matched_dept else None),
                department_name=src.department_name,
                announcement_count=counts_by_source.get(src.source_id, 0),
            )
        )
    return sources
