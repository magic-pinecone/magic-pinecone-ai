"""Endpoints for international students.

`/intl/graduation-language` serves the language requirements a student must meet
to graduate. Every rule names the official sources it was transcribed from, so a
client can always show where a number comes from. `/intl/programs` lists the
programs open to international degree students with their teaching language.

`/intl/services/search`, `/intl/contacts` and `/intl/nhi-enrollment-date` cover
everyday administration: what the Office of International Affairs pages say
about insurance, immigration and work permits, whom to ask, and when a new
student can join National Health Insurance.

`/intl/guides` is a small library of central-government pages (Ministry of
Labor, National Immigration Agency, NHI Administration, Ministry of Education)
on work permits, insurance and residence, with their text and source links.
"""

from collections.abc import Iterable
from datetime import date
from typing import Any, Literal

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field
from sqlmodel import Session

from app.api.deps import SessionDep
from app.services import gov_guides, student_services
from app.services import graduation_language as service
from app.services.translator import translate

router = APIRouter(prefix="/intl", tags=["intl"])

Degree = Literal["bachelor", "master", "phd"]
# How the admission system classifies a program: taught in Chinese, enough
# English-taught courses to graduate, or taught entirely in English.
TeachingLanguage = Literal["chinese", "english_sufficient", "english"]


class LocalizedText(BaseModel):
    zh: str
    en: str


class Threshold(BaseModel):
    value: str
    # Admission academic years (ROC calendar) the value applies to; absent = any.
    cohort_min: int | None = None
    cohort_max: int | None = None
    note: str | None = None


class OtherLanguageTest(BaseModel):
    language: str
    test: str
    requirement: str


class Rule(BaseModel):
    id: str
    degree: Degree
    college: LocalizedText
    # Empty when the rule covers the whole college.
    department: LocalizedText | None = None
    apply_at: LocalizedText | None = None
    summary: LocalizedText | None = None
    # Test id (a key of `tests`) -> accepted scores.
    english: dict[str, list[Threshold]] = {}
    other_languages: list[OtherLanguageTest] = []
    other_languages_without_threshold: list[str] = []
    alternatives: list[str] = []
    international_students: list[str] = []
    notes: list[str] = []
    sources: list[str]


class Source(BaseModel):
    id: str
    title: str
    url: str
    org: str
    lang: str
    degree_levels: list[str]
    effective: str = ""
    extract: str = "html"


class GraduationLanguageResponse(BaseModel):
    # The day the numbers were last compared with the official sources.
    verified_on: str
    disclaimer: LocalizedText
    tests: dict[str, LocalizedText]
    general: dict[str, Any]
    # Chinese-language rules that bear on graduation, and ones often mistaken for them.
    chinese: dict[str, Any]
    rules: list[Rule]
    sources: list[Source]
    # Unofficial English renderings of the Chinese strings above, keyed by the
    # Chinese text. A string with no entry has no translation need (a number).
    en: dict[str, str]


class ProgramGraduation(BaseModel):
    # As printed by the admission system; empty when the department left it blank.
    years: str = ""
    credits: str = ""
    elective_credits: str = ""
    required_credits: str = ""


class Program(BaseModel):
    name: LocalizedText
    degree: Degree
    teaching_language: TeachingLanguage
    college: LocalizedText
    admission_language_note: LocalizedText
    graduation: ProgramGraduation
    # The department's own graduation rules, when the admission system links them.
    regulation_url: str | None
    website: str | None


class ProgramsResponse(BaseModel):
    intake: str
    source_url: str
    fetched_on: str
    programs: list[Program]


@router.get("/programs", response_model=ProgramsResponse)
def get_programs(
    degree: Degree | None = None,
    teaching_language: TeachingLanguage | None = None,
    q: str | None = Query(
        default=None, description="Part of a program or college name, either language"
    ),
) -> Any:
    data = service.load_programs()
    programs = service.find_programs(
        degree=degree, teaching_language=teaching_language, query=q
    )
    return {**data, "programs": programs}


@router.get("/graduation-language", response_model=GraduationLanguageResponse)
def get_graduation_language(
    degree: Degree | None = None,
    college: str | None = Query(
        default=None, description="Rule id, or the college's Chinese or English name"
    ),
    cohort: int | None = Query(
        default=None,
        ge=90,
        le=200,
        description="Admission academic year in the ROC calendar, e.g. 115",
    ),
) -> Any:
    data = service.load_requirements()
    rules = service.find_rules(degree=degree, college=college, cohort=cohort)
    cited = {source_id for rule in rules for source_id in rule["sources"]}
    cited.update(data["general"]["sources"], data["chinese"]["sources"])
    return {
        "verified_on": data["verified_on"],
        "disclaimer": data["disclaimer"],
        "tests": data["tests"],
        "general": data["general"],
        "chinese": data["chinese"],
        "rules": rules,
        "sources": [s for s in service.load_sources() if s["id"] in cited],
        "en": service.load_english(),
    }


class ServicePassage(BaseModel):
    id: str
    title: str
    topic: str
    url: str
    # The day the page was fetched; the passage is the page's text as of then.
    fetched_on: str
    matched_words: list[str]
    passage: str


class ServiceSearchResponse(BaseModel):
    topics: list[str]
    passages: list[ServicePassage]


@router.get("/services/search", response_model=ServiceSearchResponse)
def search_services(
    q: str = Query(min_length=1, description="English keywords, e.g. work permit"),
    topic: str | None = Query(default=None, description="One of `topics`"),
    limit: int = Query(default=4, ge=1, le=10),
) -> Any:
    """Passages of the OIA pages that share words with `q`, best first."""
    if topic and topic not in student_services.topics():
        raise HTTPException(status_code=422, detail="Unknown topic")
    return {
        "topics": student_services.topics(),
        "passages": student_services.search(q, topic=topic, limit=limit),
    }


class Office(BaseModel):
    id: str
    name: str
    topics: list[str]
    phone: str
    # Dialled after `switchboard`.
    extension: str
    duties: list[str]
    location: str
    # Id of the building in `GET /campus/places`.
    place_id: str
    hours: str
    url: str


class ContactsResponse(BaseModel):
    verified_on: str
    switchboard: str
    # Lists who currently holds each duty; names are not served here.
    staff_page: str
    topics: list[str]
    offices: list[Office]


@router.get("/contacts", response_model=ContactsResponse)
def get_contacts(
    topic: str | None = Query(default=None, description="One of `topics`"),
) -> Any:
    contacts = student_services.load_contacts()
    return {
        **contacts,
        "topics": student_services.contact_topics(),
        "offices": student_services.find_contacts(topic),
    }


class TripAbroad(BaseModel):
    departure: date
    reentry: date


class NhiEnrollmentRequest(BaseModel):
    # First arrival in Taiwan.
    arrival_date: date
    trips_abroad: list[TripAbroad] = Field(default=[], max_length=20)


class SourceLink(BaseModel):
    id: str
    title: str
    url: str


class NhiEnrollmentResponse(BaseModel):
    arrival: date
    nhi_enrollment_date: date
    # The last day the student still needs the private insurance.
    private_insurance_until: date
    counting_restarted: bool
    steps: list[str]
    assumptions: list[str]
    disclaimer: str
    source: SourceLink


@router.post("/nhi-enrollment-date", response_model=NhiEnrollmentResponse)
def estimate_nhi_enrollment_date(request: NhiEnrollmentRequest) -> Any:
    """Estimate when a new student can enrol in National Health Insurance."""
    trips = [(trip.departure, trip.reentry) for trip in request.trips_abroad]
    try:
        return student_services.nhi_enrollment(request.arrival_date, trips)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


class GuideOriginal(BaseModel):
    lang: str
    title: str
    org: str
    body: str


class GuideArticle(BaseModel):
    id: str
    title: str
    # The issuing agency, or the newspaper for a news report.
    org: str
    lang: str
    topic: str
    url: str
    # An online service to open, not a page to read: `body` is empty.
    is_service: bool
    # The day the text was fetched; empty for a service.
    fetched_on: str
    body: str
    # The agency's own wording when title, org and body are machine-translated.
    original: GuideOriginal | None = None


class GuidesResponse(BaseModel):
    topics: list[str]
    articles: list[GuideArticle]


def _translate_guide_lines(
    session: Session, texts: Iterable[str], lang: str
) -> dict[str, str]:
    """Google's translations (cached), with the shipped Argos translations filling the
    lines Google could not translate, e.g. while it rate-limits this server."""
    texts = list(texts)
    live = translate(session, texts, lang)
    shipped = gov_guides.shipped_translations(gov_guides.shipped_code(lang))
    return {t: shipped[t] for t in texts if t in shipped and t not in live} | live


@router.get("/guides", response_model=GuidesResponse)
def get_guides(
    session: SessionDep,
    topic: str | None = Query(default=None, description="One of `topics`"),
    lang: str | None = Query(
        default=None,
        pattern=r"^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}$",
        description="Language tag such as en or vi; machine-translates title, org "
        "and body of the guides written in another language. Text that fails to "
        "translate stays as written.",
    ),
) -> Any:
    """Government guides for international students, grouped by topic."""
    topics = gov_guides.topics()
    if topic and topic not in topics:
        raise HTTPException(status_code=422, detail="Unknown topic")
    articles = [
        a for a in gov_guides.load_articles() if not topic or a["topic"] == topic
    ]
    if lang:
        articles = gov_guides.localize(
            articles,
            lang,
            lambda texts, _source: _translate_guide_lines(session, texts, lang),
        )
    return {"topics": topics, "articles": articles}
