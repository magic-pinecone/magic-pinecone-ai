"""Endpoints for international students.

`/intl/graduation-language` serves the language requirements a student must meet
to graduate. Every rule names the official sources it was transcribed from, so a
client can always show where a number comes from. `/intl/programs` lists the
programs open to international degree students with their teaching language.
"""

from typing import Any, Literal

from fastapi import APIRouter, Query
from pydantic import BaseModel

from app.services import graduation_language as service

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
