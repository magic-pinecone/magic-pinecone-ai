"""Catalog endpoints: the contract the Magic Pinecone apps consume.

The apps download one whole semester and search it on the device, so these
endpoints return the same JSON shape as the static files published by
magic-pinecone-lite (`courses.json` and `detail/<serial_no>.json`).
"""

import logging
import re
from datetime import UTC, datetime, timedelta
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from sqlmodel import select

from app.api.deps import SessionDep
from app.core.config import settings
from app.models.catalog import CATALOG_STATE_ID, CatalogState, CourseDetail
from app.models.course import College, Course, Department
from app.services import course_detail as course_detail_service
from app.services.course_translations import load_table, normalize_lang, translate

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/catalog", tags=["catalog"])

PasswordCard = Literal["ALL", "OPTIONAL", "NONE"]
CourseType = Literal["REQUIRED", "ELECTIVE", "UNKNOWN"]

LangQuery = Query(
    None,
    pattern=r"^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$",
    description="Language tag such as en or vi-VN; translates the Chinese text. "
    "Lines without a translation, and other languages, stay in Chinese.",
)
SYLLABUS_FIELDS = (
    "objectives",
    "content",
    "books",
    "teaching_method",
    "grading_policy",
)


class CatalogCourse(BaseModel):
    serial_no: str
    class_no: str
    title: str
    credit: float
    password_card: PasswordCard
    teachers: list[str]
    class_times: list[str]
    limit_cnt: int
    admit_cnt: int
    wait_cnt: int
    college_name: str
    department_name: str
    course_type: CourseType
    detail_url: str


class CatalogResponse(BaseModel):
    last_updated: datetime
    academic_year: str
    semester: str
    courses: list[CatalogCourse]


class CatalogStatus(BaseModel):
    # "<academic year>-<term>", the value to put in the catalog URLs.
    semester: str
    last_updated: datetime
    course_count: int


class DistributionCondition(BaseModel):
    priority: int
    rule: str


class CourseDetailResponse(BaseModel):
    serial_no: str
    objectives: str
    content: str
    books: str
    teaching_method: str
    grading_policy: str
    distribution_conditions: list[DistributionCondition]


def format_serial_no(serial_no: int) -> str:
    return str(serial_no).zfill(5)


def _as_utc(value: datetime) -> datetime:
    # SQLite hands back naive datetimes; everything we store is UTC.
    return value if value.tzinfo else value.replace(tzinfo=UTC)


def _require_semester(session: SessionDep, semester: str) -> CatalogState:
    state = session.get(CatalogState, CATALOG_STATE_ID)
    if state is None:
        raise HTTPException(
            status_code=503,
            detail="The course catalog has not been synced yet. "
            "Trigger POST /scraper/trigger and retry.",
        )
    if state.semester != semester:
        raise HTTPException(
            status_code=404,
            detail=f"Semester {semester} is not available; "
            f"the catalog holds {state.semester}.",
        )
    return state


def _to_catalog_course(
    course: Course, departments: dict[str, Department], colleges: dict[str, str]
) -> CatalogCourse:
    # A cross-listed course reports the last department that lists it, the same
    # choice magic-pinecone-lite makes, so both sources filter identically.
    department = next(
        (departments[d] for d in reversed(course.department_ids) if d in departments),
        None,
    )
    serial_no = format_serial_no(course.serial_no)
    return CatalogCourse(
        serial_no=serial_no,
        class_no=course.class_no,
        title=course.title,
        credit=course.credit,
        password_card=course.password_card
        if course.password_card in ("ALL", "OPTIONAL", "NONE")
        else "NONE",
        teachers=course.teachers or [],
        class_times=course.class_times or [],
        limit_cnt=course.limit_cnt or 0,
        admit_cnt=course.admit_cnt or 0,
        wait_cnt=course.wait_cnt or 0,
        college_name=colleges.get(department.college_id, "") if department else "",
        department_name=department.department_name if department else "",
        course_type=course.course_type
        if course.course_type in ("REQUIRED", "ELECTIVE")
        else "UNKNOWN",
        detail_url=course_detail_service.detail_url(serial_no),
    )


@router.get("", response_model=CatalogStatus)
def get_catalog_status(session: SessionDep) -> CatalogStatus:
    """Report which semester the backend currently serves."""
    state = session.get(CatalogState, CATALOG_STATE_ID)
    if state is None:
        raise HTTPException(
            status_code=503, detail="The course catalog has not been synced yet."
        )
    return CatalogStatus(
        semester=state.semester,
        last_updated=_as_utc(state.last_synced_at),
        course_count=len(session.exec(select(Course.serial_no)).all()),
    )


@router.get("/{semester}/courses", response_model=CatalogResponse)
def get_catalog(
    semester: str, session: SessionDep, lang: str | None = LangQuery
) -> CatalogResponse:
    """Return every course of a semester in one response."""
    state = _require_semester(session, semester)
    academic_year, _, term = state.semester.partition("-")

    departments = {d.department_id: d for d in session.exec(select(Department)).all()}
    colleges = {
        # NCU appends a course count to the heading, e.g. "工學院(281)".
        c.college_id: re.sub(r"\(.*?\)", "", c.college_name).strip()
        for c in session.exec(select(College)).all()
    }
    courses = session.exec(select(Course).order_by(Course.serial_no)).all()  # type: ignore[arg-type]

    catalog = [_to_catalog_course(c, departments, colleges) for c in courses]
    if target := normalize_lang(lang):
        table = load_table("catalog", target)
        catalog = [
            c.model_copy(
                update={
                    f: translate(getattr(c, f), table)
                    for f in ("title", "college_name", "department_name")
                }
            )
            for c in catalog
        ]
    return CatalogResponse(
        last_updated=_as_utc(state.last_synced_at),
        academic_year=academic_year,
        semester=term,
        courses=catalog,
    )


@router.get(
    "/{semester}/courses/{serial_no}/detail", response_model=CourseDetailResponse
)
def get_course_detail(
    semester: str, serial_no: str, session: SessionDep, lang: str | None = LangQuery
) -> CourseDetailResponse:
    """Return a course's syllabus, fetched from NCU on first use and then cached."""
    _require_semester(session, semester)
    if not serial_no.isdigit() or session.get(Course, int(serial_no)) is None:
        raise HTTPException(status_code=404, detail="Course not found")

    key = int(serial_no)
    cached = session.get(CourseDetail, key)
    max_age = timedelta(hours=settings.COURSE_DETAIL_CACHE_HOURS)
    if cached is None or datetime.now(UTC) - _as_utc(cached.fetched_at) > max_age:
        try:
            fresh = course_detail_service.fetch_course_detail(format_serial_no(key))
        except httpx.HTTPError as exc:
            logger.warning(f"Could not fetch detail for course {serial_no}: {exc}")
            if cached is None:
                raise HTTPException(
                    status_code=502, detail="NCU course detail page is unreachable"
                ) from exc
            # A stale syllabus beats none while NCU is down.
        else:
            if fresh is None:
                if cached is not None:
                    session.delete(cached)
                    session.commit()
                cached = None
            else:
                cached = cached or CourseDetail(serial_no=key)
                for field, value in fresh.items():
                    setattr(cached, field, value)
                cached.fetched_at = datetime.now(UTC)
                session.add(cached)
                session.commit()
                session.refresh(cached)

    if cached is None:
        raise HTTPException(status_code=404, detail="This course has no syllabus")

    detail = CourseDetailResponse(
        serial_no=format_serial_no(key),
        objectives=cached.objectives,
        content=cached.content,
        books=cached.books,
        teaching_method=cached.teaching_method,
        grading_policy=cached.grading_policy,
        distribution_conditions=cached.distribution_conditions,
    )
    target = normalize_lang(lang)
    if target is None:
        return detail
    syllabus = load_table("detail", target)
    rules = load_table("catalog", target)
    return detail.model_copy(
        update={f: translate(getattr(detail, f), syllabus) for f in SYLLABUS_FIELDS}
        | {
            "distribution_conditions": [
                DistributionCondition(
                    priority=c.priority, rule=translate(c.rule, rules)
                )
                for c in detail.distribution_conditions
            ]
        }
    )
