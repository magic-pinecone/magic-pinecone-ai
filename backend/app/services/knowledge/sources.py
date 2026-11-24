import json
import re
from collections.abc import Iterable
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

from sqlmodel import Session, select

from app.models.catalog import CatalogState, CourseDetail
from app.models.club import Club
from app.models.course import Course, Department
from app.services import (
    campus_places,
    credits,
    destinations,
    gov_guides,
    graduation_language,
    service_learning,
    student_services,
)
from app.services.course_detail import detail_url
from app.services.knowledge import crawler

FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
SOURCE_TYPES = (
    "oia_page",
    "gov_guide",
    "graduation_language",
    "intl_program",
    "credits",
    "service_learning",
    "contact",
    "place",
    "destination",
    "course",
    "club",
)
CLUB_SITE = "https://club.adm.ncu.edu.tw"
WEEKDAYS = {
    "1": "Mon 週一",
    "2": "Tue 週二",
    "3": "Wed 週三",
    "4": "Thu 週四",
    "5": "Fri 週五",
    "6": "Sat 週六",
    "7": "Sun 週日",
}
SYLLABUS_CHARS = 600


@dataclass(frozen=True)
class Document:
    id: str
    title: str
    url: str
    topic: str
    source_type: str
    lang: str
    fetched_on: str
    lines: tuple[str, ...]


def _lines(*parts: Any) -> tuple[str, ...]:
    found = []
    for part in parts:
        if part is None:
            continue
        for line in str(part).split("\n"):
            if line.strip():
                found.append(line.strip())
    return tuple(found)


def _read_corpus(path: Path) -> tuple[dict[str, str], tuple[str, ...]]:
    text = path.read_text(encoding="utf-8")
    head = FRONT_MATTER.match(text)
    meta: dict[str, str] = {}
    if head:
        for row in head.group(1).split("\n"):
            key, _, value = row.partition(": ")
            try:
                meta[key] = json.loads(value)
            except json.JSONDecodeError:
                meta[key] = value.strip('"')
    body = FRONT_MATTER.sub("", text)
    lines = tuple(line for line in body.split("\n") if line.strip())
    if lines and lines[0] == f"# {meta.get('title', '')}":
        lines = lines[1:]
    return meta, lines


def _canonical(url: str) -> str:
    return crawler.normalize(url) or url


def oia_pages() -> list[Document]:
    documents = [
        Document(
            id=doc.id,
            title=doc.title,
            url=doc.url,
            topic=doc.topic,
            source_type="oia_page",
            lang="en",
            fetched_on=doc.fetched_on,
            lines=doc.lines,
        )
        for doc in student_services.load_documents()
    ]
    known = {_canonical(doc.url) for doc in documents}
    corpus = crawler.DATA_DIR / "corpus"
    for path in sorted(corpus.glob("*.md")) if corpus.exists() else []:
        meta, lines = _read_corpus(path)
        if not lines or _canonical(meta.get("url", "")) in known:
            continue
        documents.append(
            Document(
                id=meta.get("id", path.stem),
                title=meta.get("title", path.stem),
                url=meta.get("url", ""),
                topic=meta.get("topic", "oia_site"),
                source_type="oia_page",
                lang=meta.get("lang", ""),
                fetched_on=meta.get("fetched_on", ""),
                lines=lines,
            )
        )
    return documents


GOV_GUIDE_TITLES_EN = {
    "mol-work-permit-simplified": "Simpler work permit documents for overseas "
    "Chinese and foreign students",
    "wda-summer-hours": "Work permit for overseas Chinese and foreign students: "
    "no 20-hour weekly limit in winter and summer vacations",
    "ocac-work-permit": "How overseas Chinese students apply for a work permit",
    "nhi-students": "National Health Insurance for overseas Chinese and foreign "
    "students in Taiwan",
    "mol-student-labor-insurance": "Labor insurance, employment insurance and "
    "labor pension for student workers",
    "nisa-faq-arc": "Resident certificate (ARC) for international students: who "
    "applies, deadlines and steps",
    "nisa-faq-graduation-extension": "Resident certificate deadlines and staying "
    "after graduation",
    "nia-stay-after-graduation": "Staying in Taiwan after graduation and avoiding "
    "scams",
    "nia-hotline": "Service contacts and the 1990 hotline for foreigners in Taiwan",
}


def gov_guide_documents() -> list[Document]:
    return [
        Document(
            id=article["id"],
            title=" / ".join(
                filter(None, (GOV_GUIDE_TITLES_EN.get(article["id"]), article["title"]))
            ),
            url=article["url"],
            topic=article["topic"],
            source_type="gov_guide",
            lang=article["lang"],
            fetched_on=article["fetched_on"],
            lines=_lines(article["body"]),
        )
        for article in gov_guides.load_articles()
        if article["body"]
    ]


def _thresholds(english: dict[str, list[dict[str, Any]]]) -> list[str]:
    rows = []
    for test, thresholds in english.items():
        for threshold in thresholds:
            low, high = threshold.get("cohort_min"), threshold.get("cohort_max")
            cohort = (
                f" (admitted {low}–{high})"
                if low and high
                else f" (admitted {low} or later)"
                if low
                else f" (admitted {high} or earlier)"
                if high
                else ""
            )
            rows.append(f"{test}: {threshold['value']}{cohort}")
    return rows


def graduation_language_documents() -> list[Document]:
    data = graduation_language.load_requirements()
    urls = {
        source["id"]: source["url"] for source in graduation_language.load_sources()
    }
    documents = []
    for rule in data["rules"]:
        college = rule["college"]
        apply_at = rule.get("apply_at") or {}
        documents.append(
            Document(
                id=f"graduation-language-{rule['id']}",
                title=f"{college['zh']} {college['en']} {rule['degree']} graduation English requirement 畢業英語門檻",
                url=next((urls[s] for s in rule.get("sources", []) if s in urls), ""),
                topic="graduation_language",
                source_type="graduation_language",
                lang="zh-TW",
                fetched_on=data["verified_on"],
                lines=_lines(
                    f"Degree: {rule['degree']}",
                    f"Apply at: {apply_at.get('zh', '')} {apply_at.get('en', '')}".strip(),
                    *_thresholds(rule.get("english", {})),
                    *rule.get("notes", []),
                ),
            )
        )
    return documents


def intl_program_documents() -> list[Document]:
    data = graduation_language.load_programs()
    documents = []
    for index, program in enumerate(data["programs"]):
        name, college = program["name"], program["college"]
        graduation = program.get("graduation") or {}
        note = program.get("admission_language_note") or {}
        documents.append(
            Document(
                id=f"intl-program-{index}",
                title=f"{name['zh']} {name['en']}",
                url=program.get("website") or data.get("source_url", ""),
                topic="intl_program",
                source_type="intl_program",
                lang="zh-TW",
                fetched_on=data.get("fetched_on", ""),
                lines=_lines(
                    f"College: {college['zh']} {college['en']}",
                    f"Degree: {program['degree']}; teaching language: {program['teaching_language']}",
                    f"Admission language: {note.get('en', '')} {note.get('zh', '')}".strip(),
                    "Graduation: "
                    + ", ".join(f"{k} {v}" for k, v in graduation.items() if v),
                    f"Regulations: {program.get('regulation_url', '')}",
                ),
            )
        )
    return documents


def _rule_documents(data: Any, source_type: str) -> list[Document]:
    documents = []
    for requirement in data.requirements:
        source = requirement.sources[0]
        documents.append(
            Document(
                id=f"{source_type}-{requirement.id}",
                title=f"{requirement.title_en} {requirement.title_zh}",
                url=source.url,
                topic=source_type,
                source_type=source_type,
                lang="en",
                fetched_on=source.fetched,
                lines=_lines(
                    f"Degrees: {', '.join(requirement.degrees)}",
                    requirement.summary_en,
                    requirement.summary_zh,
                    *(f"{s.title}: {s.quote}" for s in requirement.sources),
                    f"Contact: {data.contact.name_en} {data.contact.name_zh} {data.contact.website}",
                ),
            )
        )
    return documents


def contact_documents() -> list[Document]:
    data = student_services.load_contacts()
    return [
        Document(
            id=f"contact-{office['id']}",
            title=office["name"],
            url=data.get("staff_page", ""),
            topic="contact",
            source_type="contact",
            lang="en",
            fetched_on=data["verified_on"],
            lines=_lines(
                f"Topics: {', '.join(office.get('topics', []))}",
                f"Phone: {office.get('phone', '')} ext. {office.get('extension', '')}",
                f"E-mail: {office.get('email', '')}" if office.get("email") else None,
                *office.get("duties", []),
                f"Location: {office.get('location', '')}",
                f"Hours: {office.get('hours', '')}",
            ),
        )
        for office in data["offices"]
    ]


def place_documents() -> list[Document]:
    data = campus_places.load()
    return [
        Document(
            id=f"place-{place.id}",
            title=f"{place.name_zh} {place.name_en}",
            url=place.map_links.google_maps,
            topic="place",
            source_type="place",
            lang="zh-TW",
            fetched_on="",
            lines=_lines(
                f"Building code: {place.code}" if place.code else None,
                f"Category: {place.category}",
                f"Also called: {', '.join(place.aliases)}" if place.aliases else None,
                place.note_en,
                f"Walking directions: {place.map_links.google_walking_directions}",
            ),
        )
        for place in data.places
    ]


def destination_documents() -> list[Document]:
    return [
        Document(
            id=f"destination-{d.id}",
            title=f"{d.name.zh} {d.name.en}",
            url=d.official_url or d.maps_url,
            topic="destination",
            source_type="destination",
            lang="zh-TW",
            fetched_on=d.checked_on.isoformat(),
            lines=_lines(
                f"Category: {d.category}",
                f"Address: {d.address.zh} {d.address.en}" if d.address else None,
                f"Phone: {d.phone}" if d.phone else None,
                f"Hours: {d.hours.en} {d.hours.zh}" if d.hours else None,
                f"Getting there: {d.getting_there.en}",
                d.getting_there.zh,
                f"Bus routes: {', '.join(d.bus_routes)}" if d.bus_routes else None,
                f"Directions from NCU: {d.maps_url}",
            ),
        )
        for d in destinations.load()
    ]


@lru_cache
def file_documents() -> tuple[Document, ...]:
    return tuple(
        [
            *oia_pages(),
            *gov_guide_documents(),
            *graduation_language_documents(),
            *intl_program_documents(),
            *_rule_documents(credits.load(), "credits"),
            *_rule_documents(service_learning.load(), "service_learning"),
            *contact_documents(),
            *place_documents(),
            *destination_documents(),
        ]
    )


def _class_times(codes: Iterable[str]) -> str:
    slots: dict[str, list[str]] = {}
    for code in codes:
        day, _, period = code.partition("-")
        slots.setdefault(day, []).append(period)
    return "; ".join(
        f"{WEEKDAYS.get(day, day)} periods {','.join(periods)}"
        for day, periods in slots.items()
    )


def _excerpt(text: str) -> str:
    text = " ".join(text.split())
    return text if len(text) <= SYLLABUS_CHARS else text[:SYLLABUS_CHARS] + "…"


def course_documents(session: Session) -> list[Document]:
    state = session.get(CatalogState, 1)
    semester = state.semester if state else ""
    fetched = state.last_synced_at.date().isoformat() if state else ""
    departments = {
        d.department_id: d.department_name for d in session.exec(select(Department))
    }
    details = {d.serial_no: d for d in session.exec(select(CourseDetail))}
    documents = []
    for course in session.exec(select(Course)):
        detail = details.get(course.serial_no)
        documents.append(
            Document(
                id=f"course-{course.serial_no}",
                title=f"{course.title} ({course.class_no})",
                url=detail_url(str(course.serial_no)),
                topic=semester,
                source_type="course",
                lang="zh-TW",
                fetched_on=fetched,
                lines=_lines(
                    f"Semester {semester}; serial number {course.serial_no}; course code {course.class_no}",
                    f"Teachers 授課教師: {', '.join(course.teachers)}"
                    if course.teachers
                    else None,
                    f"Time 上課時間: {_class_times(course.class_times)}"
                    if course.class_times
                    else None,
                    f"Credits 學分: {course.credit:g}; type: {course.course_type or ''}",
                    "Departments 開課系所: "
                    + ", ".join(departments.get(d, d) for d in course.department_ids),
                    f"Objectives 課程目標: {_excerpt(detail.objectives)}"
                    if detail and detail.objectives
                    else None,
                    f"Content 課程內容: {_excerpt(detail.content)}"
                    if detail and detail.content
                    else None,
                    f"Grading 評分: {_excerpt(detail.grading_policy)}"
                    if detail and detail.grading_policy
                    else None,
                ),
            )
        )
    return documents


def club_documents(session: Session) -> list[Document]:
    return [
        Document(
            id=f"club-{club.club_id}",
            title=f"{club.name} {club.name_en}".strip(),
            url=club.website or f"{CLUB_SITE}/clubs",
            topic=club.category,
            source_type="club",
            lang="zh-TW",
            fetched_on=club.updated_at.date().isoformat(),
            lines=_lines(
                _excerpt(club.description),
                _excerpt(club.description_en) if club.description_en else None,
                f"Office 社辦: {club.address}" if club.address else None,
                f"Website: {club.website}" if club.website else None,
            ),
        )
        for club in session.exec(select(Club))
    ]


def database_documents(session: Session) -> list[Document]:
    return [*course_documents(session), *club_documents(session)]
