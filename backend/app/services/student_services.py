"""Everyday administrative help for international students.

Three things, all read from `app/data/student_services/` and all traceable to a
page of NCU's Office of International Affairs (OIA):

- a passage search over the OIA pages on insurance, immigration and work
  permits, plus the OIA documents already kept with the graduation data;
- the offices to contact, by topic;
- the date a new student can enrol in National Health Insurance, computed from
  the rule and worked examples on the OIA insurance page.

Nothing is generated. A question that shares no telling word with the pages
comes back empty, and the date calculation states the assumptions it had to
make.
"""

import calendar
import json
import math
import re
import unicodedata
from collections import Counter
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

GLOSSARY_LANGS = ("zh-TW", "vi", "id")

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


# A run of Chinese characters, or a word in any other script. Accented letters
# stay inside their word: "nhiêu" is one word, not "nhi" + "ê" + "u".
_TOKEN = re.compile(r"[\u4e00-\u9fff]+|[^\W_\u4e00-\u9fff]+")
_CHINESE = re.compile(r"[\u4e00-\u9fff]")


def _tokens(text: str) -> list[str]:
    return _TOKEN.findall(unicodedata.normalize("NFC", text).casefold())


def _stem(word: str) -> str:
    """`word` without a plural "s", so "permits" and "permit" are one word."""
    plural = len(word) > 3 and word.endswith("s") and not word.endswith("ss")
    return word[:-1] if plural and word.isascii() and word.isalpha() else word


def _words(text: str) -> list[str]:
    found = []
    for word in _tokens(text):
        if len(word) > 2 and _CHINESE.match(word):
            # Chinese has no spaces: a whole clause never matches, its bigrams do.
            found += [word[i : i + 2] for i in range(len(word) - 1)]
        else:
            found.append(word)
    return [word for word in dict.fromkeys(found) if word not in STOPWORDS]


@dataclass(frozen=True)
class Passage:
    document: Document
    start: int
    text: str
    # What a query is matched against: the passage under its document's title.
    folded: str
    stems: Counter


@lru_cache
def _passages() -> tuple[Passage, ...]:
    passages = []
    for document in load_documents():
        for start in range(0, max(1, len(document.lines)), PASSAGE_STEP):
            text = "\n".join(document.lines[start : start + PASSAGE_LINES])
            titled = unicodedata.normalize("NFC", f"{document.title}\n{text}")
            passages.append(
                Passage(
                    document=document,
                    start=start,
                    text=text,
                    folded=titled.casefold(),
                    stems=Counter(_stem(token) for token in _tokens(titled)),
                )
            )
    return tuple(passages)


def _count(word: str, passage: Passage) -> int:
    """How often `word` is in the passage or its title.

    A word in Latin letters is counted as a whole word ("arc" is not in
    "March"); Chinese has no spaces, so Chinese is counted wherever it occurs.
    """
    if _CHINESE.match(word):
        return passage.folded.count(word)
    return passage.stems[_stem(word)]


@lru_cache(maxsize=4096)
def _weight(word: str) -> float:
    """How much a word tells passages apart: rare words weigh more, absent ones 0."""
    passages = _passages()
    holding = sum(1 for passage in passages if _count(word, passage))
    return math.log(1 + len(passages) / holding) if holding else 0.0


def _min_score() -> float:
    """The least a passage must score unless it has every word of the query.

    It is the weight of a word found in a single passage: common words such
    as "student" and "apply" do not add up to a match on their own.
    """
    return math.log(1 + len(_passages()))


@lru_cache
def load_glossary() -> tuple[tuple[re.Pattern[str], str], ...]:
    """(how a term is found in a question, the English words to add) pairs."""
    data = json.loads((DATA_DIR / "glossary.json").read_text(encoding="utf-8"))
    pairs = []
    for entry in data["terms"]:
        for lang in GLOSSARY_LANGS:
            for term in entry.get(lang, []):
                term = unicodedata.normalize("NFC", term).casefold()
                # Chinese anywhere in the text, other scripts as whole words.
                pattern = (
                    re.escape(term)
                    if _CHINESE.search(term)
                    else rf"(?<!\w){re.escape(term)}(?!\w)"
                )
                pairs.append((re.compile(pattern), entry["en"]))
    return tuple(pairs)


def english_terms(text: str) -> list[str]:
    """The pages' English words for the glossary terms found in `text`."""
    folded = unicodedata.normalize("NFC", text).casefold()
    found = [english for pattern, english in load_glossary() if pattern.search(folded)]
    return list(dict.fromkeys(found))


def search(query: str, topic: str | None = None, limit: int = 5) -> list[dict]:
    """The passages that share the most telling words with `query`, best first.

    Chinese, Vietnamese and Indonesian terms of `glossary.json` are searched
    by the English words the pages use. A passage scores the summed weight of
    the distinct query words found in it or in its document's title; a word
    found in few passages weighs more. A passage that has neither every word
    of the query nor a score of `_min_score()` is left out, so a question the
    pages say nothing about comes back empty. Several passages of one document
    can be returned, but never two that overlap.
    """
    words = _words(" ".join([query, *english_terms(query)]))
    scored = []
    for passage in _passages():
        if topic and passage.document.topic != topic:
            continue
        counts = [_count(word, passage) for word in words]
        matched = [word for word, count in zip(words, counts, strict=True) if count]
        score = round(sum(_weight(word) for word in matched), 6)
        if matched and (len(matched) == len(words) or score >= _min_score()):
            scored.append(((score, sum(counts)), passage, matched))
    # Stable, so equal scores keep the order of the documents and their lines.
    scored.sort(key=lambda item: item[0], reverse=True)
    chosen: list[dict] = []
    taken: dict[str, list[int]] = {}
    for _, passage, matched in scored:
        document = passage.document
        if len(chosen) >= max(1, limit):
            break
        starts = taken.setdefault(document.id, [])
        if any(abs(passage.start - other) < PASSAGE_LINES for other in starts):
            continue
        starts.append(passage.start)
        chosen.append(
            {
                "id": document.id,
                "title": document.title,
                "topic": document.topic,
                "url": document.url,
                "fetched_on": document.fetched_on,
                "matched_words": matched,
                "passage": passage.text,
            }
        )
    return chosen


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
