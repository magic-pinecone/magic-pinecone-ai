"""Everyday administrative help for international students.

Three things, all read from `app/data/student_services/` and all traceable to a
page of NCU's Office of International Affairs (OIA):

- a passage search over the OIA pages on insurance, immigration and work
  permits, plus the OIA documents already kept with the graduation data;
- the offices to contact, by topic;
- the date a new student can enrol in National Health Insurance, computed from
  the rule and worked examples on the OIA insurance page.

Nothing is generated. A question the pages do not answer comes back empty, and
the date calculation states the assumptions it had to make.
"""

import calendar
import json
import re
from dataclasses import dataclass
from datetime import date, timedelta
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.services import graduation_language

DATA_DIR = Path(__file__).resolve().parent.parent / "data/student_services"
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
OIA_ORG_ZH = "國際事務處"

# Lines of a page kept together as one passage, and how far passages overlap.
PASSAGE_LINES = 8
PASSAGE_STEP = 5
# Words too common to tell one passage from another.
STOPWORDS = frozenset(
    "a an and are as at be can do does for how i in is it my of on or the to "
    "what when where which who will with you your".split()
)

NHI_WAIT_MONTHS = 6
NHI_LONG_TRIP_DAYS = 30
NHI_SOURCE_ID = "oia-insurance"


@lru_cache
def load_sources() -> list[dict[str, Any]]:
    data = json.loads((DATA_DIR / "sources.json").read_text(encoding="utf-8"))
    return data["sources"]


@lru_cache
def load_contacts() -> dict[str, Any]:
    return json.loads((DATA_DIR / "contacts.json").read_text(encoding="utf-8"))


@dataclass(frozen=True)
class Document:
    id: str
    title: str
    url: str
    topic: str
    fetched_on: str
    lines: tuple[str, ...]


def _read(path: Path, source: dict[str, Any], topic: str) -> Document:
    text = path.read_text(encoding="utf-8")
    head = FRONT_MATTER.match(text)
    fetched = re.search(r'^fetched_on: "(.*)"$', head.group(1), re.M) if head else None
    body = FRONT_MATTER.sub("", text)
    return Document(
        id=source["id"],
        title=source["title"],
        url=source["url"],
        topic=topic,
        fetched_on=fetched.group(1) if fetched else "",
        lines=tuple(line for line in body.split("\n") if line.strip()),
    )


@lru_cache
def load_documents() -> tuple[Document, ...]:
    """The OIA pages, followed by the OIA documents of the graduation corpus."""
    documents = [
        _read(DATA_DIR / "corpus" / f"{source['id']}.md", source, source["topic"])
        for source in load_sources()
    ]
    documents += [
        _read(
            graduation_language.DATA_DIR / "corpus" / f"{source['id']}.md",
            source,
            "general",
        )
        for source in graduation_language.load_sources()
        if source["org"] == OIA_ORG_ZH
    ]
    return tuple(documents)


def topics() -> list[str]:
    return sorted({document.topic for document in load_documents()})


def _words(text: str) -> list[str]:
    found = re.findall(r"[0-9a-z]+|[^\W\d_a-zA-Z]+", text.casefold())
    return [word for word in dict.fromkeys(found) if word not in STOPWORDS]


def search(query: str, topic: str | None = None, limit: int = 5) -> list[dict]:
    """The passages that share the most words with `query`, best first.

    A passage matches when it has at least one of the words; passages covering
    more distinct words rank higher, then those repeating them more. Only the
    best passage of each document is returned.
    """
    words = _words(query)
    if not words:
        return []
    best: dict[str, tuple[tuple[int, int], dict]] = {}
    for document in load_documents():
        if topic and document.topic != topic:
            continue
        title = document.title.casefold()
        for start in range(0, max(1, len(document.lines)), PASSAGE_STEP):
            passage = "\n".join(document.lines[start : start + PASSAGE_LINES])
            folded = passage.casefold()
            counts = [folded.count(word) for word in words]
            covered = sum(1 for count in counts if count)
            if not covered:
                continue
            score = (covered + sum(word in title for word in words), sum(counts))
            if document.id in best and best[document.id][0] >= score:
                continue
            best[document.id] = (
                score,
                {
                    "id": document.id,
                    "title": document.title,
                    "topic": document.topic,
                    "url": document.url,
                    "fetched_on": document.fetched_on,
                    "matched_words": [
                        w for w, c in zip(words, counts, strict=True) if c
                    ],
                    "passage": passage,
                },
            )
    ranked = sorted(best.values(), key=lambda item: item[0], reverse=True)
    return [passage for _, passage in ranked[: max(1, limit)]]


def find_contacts(topic: str | None = None) -> list[dict[str, Any]]:
    """Offices handling `topic`; every office when no topic is given."""
    offices = load_contacts()["offices"]
    if not topic:
        return offices
    return [office for office in offices if topic in office["topics"]]


def contact_topics() -> list[str]:
    offices = load_contacts()["offices"]
    return sorted({topic for office in offices for topic in office["topics"]})


def _add_months(day: date, months: int) -> date:
    """The same day `months` later, or the month's last day when it has no such day."""
    index = day.month - 1 + months
    year, month = day.year + index // 12, index % 12 + 1
    return date(year, month, min(day.day, calendar.monthrange(year, month)[1]))


def nhi_enrollment(arrival: date, trips: list[tuple[date, date]]) -> dict[str, Any]:
    """When a student who arrived on `arrival` can enrol in NHI.

    `trips` are (departure, re-entry) dates of journeys out of Taiwan. The OIA
    insurance page gives the rule: six months of continuous residence, during
    which one trip of fewer than 30 days only postpones the date by its length,
    while a trip of 30 days or more, or a second trip, restarts the six months
    from the latest re-entry. Days abroad are counted as the page's examples
    count them, departure and re-entry days included.
    """
    for departure, reentry in trips:
        if reentry < departure:
            raise ValueError("A trip's re-entry date is before its departure date")
        if departure < arrival:
            raise ValueError("A trip starts before the arrival date")
    ordered = sorted(trips)
    for (_, earlier_return), (later_departure, _) in zip(
        ordered, ordered[1:], strict=False
    ):
        if later_departure < earlier_return:
            raise ValueError("Two trips overlap")

    start = arrival
    postponed = 0
    short_trip_used = False
    steps = [
        f"Arrived {arrival.isoformat()}: six months of continuous residence "
        f"would end on {_add_months(arrival, NHI_WAIT_MONTHS).isoformat()}."
    ]
    restarted = False
    ignored = 0
    for departure, reentry in ordered:
        eligible = _add_months(start, NHI_WAIT_MONTHS) + timedelta(days=postponed)
        if departure >= eligible:
            ignored += 1
            continue
        days = (reentry - departure).days + 1
        span = f"{departure.isoformat()} to {reentry.isoformat()} ({days} days)"
        if days < NHI_LONG_TRIP_DAYS and not short_trip_used:
            short_trip_used = True
            postponed = days
            steps.append(
                f"Trip {span}: a single trip shorter than {NHI_LONG_TRIP_DAYS} "
                f"days postpones the date by {days} days."
            )
            continue
        reason = (
            f"{NHI_LONG_TRIP_DAYS} days or longer"
            if days >= NHI_LONG_TRIP_DAYS
            else "a second trip within the six months"
        )
        start, postponed, short_trip_used, restarted = reentry, 0, False, True
        steps.append(
            f"Trip {span}: {reason}, so the six months restart from the "
            f"re-entry date {reentry.isoformat()}."
        )
    enrollment = _add_months(start, NHI_WAIT_MONTHS) + timedelta(days=postponed)
    steps.append(f"NHI enrolment date: {enrollment.isoformat()}.")

    assumptions = [
        "Days abroad include both the departure day and the re-entry day, as in "
        "the page's examples (1 to 30 November is 30 days).",
        "Six months later means the same day of the month, or the last day of "
        "that month when it is shorter.",
    ]
    if restarted:
        assumptions.append(
            "After a restart the page does not say whether one short trip is "
            "allowed again; this calculation assumes it is."
        )
    if ignored:
        assumptions.append(
            f"{ignored} trip(s) starting on or after the enrolment date were not "
            "counted."
        )
    source = next(s for s in load_sources() if s["id"] == NHI_SOURCE_ID)
    return {
        "arrival": arrival.isoformat(),
        "nhi_enrollment_date": enrollment.isoformat(),
        "private_insurance_until": (enrollment - timedelta(days=1)).isoformat(),
        "counting_restarted": restarted,
        "steps": steps,
        "assumptions": assumptions,
        "disclaimer": (
            "An estimate from the rule on the OIA insurance page. The National "
            "Health Insurance Administration and OIA decide the actual date; "
            "confirm with OIA before relying on it."
        ),
        "source": {"id": source["id"], "title": source["title"], "url": source["url"]},
    }
