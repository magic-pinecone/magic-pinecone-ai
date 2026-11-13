"""Answers a student's question from what this backend knows.

The question is sent one of three ways, decided here in code and not by the
model (a 2B model on a phone cannot be trusted to call tools):

- bus: it has a bus word ("bus", "公車", "xe buýt", "bis"), with or without a
  route number, or asks how to get to one of the off-campus destinations.
  Live arrivals come from TDX and are returned as data, never through the
  model; the model only gets the written directions to a destination.
- place: it is a short "where is ..." question naming a building, or little
  more than a building code such as E6-A203. The building comes from the
  campus places list.
- documents: anything else. Passages come from the OIA pages.

What was found is laid out as numbered passages and, when a model is
configured (`ASSISTANT_BASE_URL`, `ASSISTANT_MODEL`), the model answers from
them in the prompt format the students are trained on. Without a model the
caller still gets what was found; a question that is not in English is then
searched as written, and finds the English documents only through the terms
of the search's glossary (`student_services.english_terms`).
"""

import logging
import re
import time
from datetime import UTC, datetime
from functools import cache
from typing import Any, Literal

import httpx

from app.core.config import settings
from app.services import campus_places, destinations, student_services, tdx
from app.services.answer_text import (
    OIA_CONTACT_STRINGS,
    build_messages,
    cited_numbers,
    is_refusal,
    strip_reasoning,
    unsupported_numbers,
)

logger = logging.getLogger(__name__)

Kind = Literal["documents", "place", "bus"]
BusProblem = Literal["not_configured", "rate_limited", "unreachable"]

TOP_K = 3
MAX_QUESTION_CHARS = 500
MAX_PLACES = 3
MAX_BUS_ROUTES = 2
BUS_SOURCE = "TDX (tdx.transportdata.tw)"

TRANSLATE_PROMPT = (
    "Translate the student's question into English. Reply with the translation "
    "only, on one line."
)
# The office's own names for things a literal translation calls something else.
# Done here, not in the prompt: a 2B model copies a glossary into every reply.
OFFICE_TERMS = (
    ("guotai", "Cathay"),
    ("cathay life", "Cathay"),
    ("residence permit", "ARC residence permit"),
    ("residence card", "ARC residence"),
    ("national health insurance", "NHI National Health Insurance"),
    ("health insurance card", "NHI card"),
)

ENGLISH_WORDS = frozenset(
    "a an and are can do does for how i in is long many much my of the to what when "
    "where which who will with".split()
)

# A route number standing on its own, with ASCII-only edges so "132公車" is
# still read. It only counts next to a bus word: see `bus_question`.
_ROUTE = re.compile(
    r"(?<![A-Za-z0-9_$.,-])(9025a?|132|133|172|173)(?![A-Za-z0-9_]|[.,]\d)", re.I
)
# A building code, optionally followed by a room of three or four digits:
# "E6", "e6-A203", "LH". ("I-20" is a visa form, not a room in building I.)
_CODE = re.compile(
    r"(?<![A-Za-z0-9_-])([A-Za-z]{1,2}\d?)(-[A-Za-z]?\d{3,4})?(?![A-Za-z0-9_-])"
)
_CJK = re.compile(r"[\u3400-\u9fff]")
_LATIN_WORD = re.compile(r"[^\W\d_]+", re.UNICODE)

# Each list is matched as whole words when written in Latin letters ("bis"
# is not found in "bisa") and anywhere in the text when written in Chinese.
BUS_WORDS = (
    "bus",
    "buses",
    "公車",
    "巴士",
    "客運",
    "xe buýt",
    "xe bus",
    "bis",
    "bus kota",
)
# "How do I get there", asked about an off-campus destination.
WAY_WORDS = (
    "get to", "travel to", "directions to", "way to",
    "怎麼去", "怎麼到", "怎麼走", "如何去", "如何到", "如何前往",
    "làm sao để đến", "làm sao đến", "cách đi", "đi như thế nào",
    "bagaimana ke", "bagaimana cara ke", "cara ke", "naik apa",
)  # fmt: skip
# "Where is it", asked about a building on campus.
WHERE_WORDS = (
    "where", "location", "located", "directions", "map",
    "在哪", "哪裡", "哪里", "位置", "怎麼走", "地圖",
    "ở đâu", "chỗ nào", "bản đồ",
    "di mana", "dimana", "lokasi", "peta",
)  # fmt: skip
# How a question names each off-campus destination.
DESTINATION_WORDS: dict[str, tuple[str, ...]] = {
    "zhongli-train-station": (
        "zhongli", "chungli", "jhongli", "train station", "中壢", "火車站",
        "trung lịch", "ga tàu", "stasiun kereta",
    ),
    "thsr-taoyuan-station": (
        "thsr", "hsr", "high speed rail", "high-speed rail", "高鐵",
        "tàu cao tốc", "kereta cepat",
    ),
    "taoyuan-airport": ("airport", "機場", "sân bay", "bandara"),
    "nia-taoyuan-service-center": (
        "immigration", "移民署", "di trú", "imigrasi",
    ),
    "nhi-northern-division": (
        "health insurance administration", "nhi office", "健保署",
    ),
}  # fmt: skip
# Shown for a bus question that names neither a route nor a destination:
# one route to Zhongli and one to the high-speed rail station.
DEFAULT_BUS_ROUTES = ("132", "172")
# A "where is X" question says little besides X. Longer ones ("where do I
# apply for the scholarship, at the OIA?") are about something else and go to
# the documents. Counted after the name and the where-word are taken out.
MAX_OTHER_LATIN_WORDS = 4
MAX_OTHER_CJK_CHARS = 5
# Prefixes a question leaves off a building's name: "the post office".
_NAME_PREFIXES = ("national central university ", "ncu ")


class ModelError(RuntimeError):
    """The model endpoint could not be used. Never carries the URL or the key."""


class ChatModel:
    """An OpenAI-compatible `/chat/completions` endpoint."""

    def __init__(
        self,
        base_url: str,
        model: str,
        api_key: str | None = None,
        reasoning_effort: str | None = None,
        timeout: float = 60.0,
        disable_thinking: bool = False,
    ):
        self.name = model
        self._url = base_url.rstrip("/") + "/chat/completions"
        headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._http = httpx.Client(headers=headers, timeout=timeout)
        # "none" stops a Gemma 4 behind Ollama from thinking before it answers,
        # which otherwise leaves the answer itself empty.
        self._extra: dict[str, Any] = (
            {"reasoning_effort": reasoning_effort} if reasoning_effort else {}
        )
        # llama-server reads the switch from here instead. A Gemma 4 it serves
        # thinks unless told not to, and the chat template then adds a marker
        # to the prompt that a tuned student never saw in training.
        if disable_thinking:
            self._extra["chat_template_kwargs"] = {"enable_thinking": False}

    def chat(self, messages: list[dict[str, str]], max_tokens: int = 512) -> str:
        payload = {
            "model": self.name,
            "messages": messages,
            "temperature": 0.0,
            "max_tokens": max_tokens,
            **self._extra,
        }
        try:
            response = self._http.post(self._url, json=payload)
        except (httpx.HTTPError, httpx.InvalidURL) as error:
            raise ModelError(type(error).__name__) from error
        if response.status_code != 200:
            raise ModelError(f"HTTP {response.status_code}")
        try:
            content = response.json()["choices"][0]["message"].get("content")
        except (ValueError, KeyError, IndexError, TypeError, AttributeError) as error:
            raise ModelError("unexpected response body") from error
        if content is not None and not isinstance(content, str):
            raise ModelError("unexpected response body")
        return content or ""


@cache
def configured_model() -> ChatModel | None:
    """The model named in the settings, or None when there is none."""
    if not settings.ASSISTANT_BASE_URL or not settings.ASSISTANT_MODEL:
        return None
    return ChatModel(
        settings.ASSISTANT_BASE_URL,
        settings.ASSISTANT_MODEL,
        settings.ASSISTANT_API_KEY,
        settings.ASSISTANT_REASONING_EFFORT,
        timeout=settings.ASSISTANT_TIMEOUT_SECONDS,
        disable_thinking=settings.ASSISTANT_DISABLE_THINKING,
    )


def looks_english(text: str) -> bool:
    """True when the question can be searched in the English documents as it is.

    Indonesian is written in plain ASCII too, so ASCII letters are not enough:
    the question also has to contain a common English function word.
    """
    letters = [char for char in text if char.isalpha()]
    if not letters or not all(char.isascii() for char in letters):
        return False
    words = "".join(char if char.isalpha() else " " for char in text.casefold()).split()
    return any(word in ENGLISH_WORDS for word in words)


def office_terms(text: str) -> str:
    """`text` with the office's names put in place of literal translations."""
    for literal, name in OFFICE_TERMS:
        if name.casefold() not in text.casefold():
            text = re.sub(re.escape(literal), name, text, flags=re.IGNORECASE)
    return text


def english_query(question: str, model: Any) -> str | None:
    """The question in English for the search, or None to search it as asked."""
    if model is None or looks_english(question):
        return None
    reply = model.chat(
        [
            {"role": "system", "content": TRANSLATE_PROMPT},
            {"role": "user", "content": question},
        ],
        max_tokens=80,
    )
    return office_terms(" ".join(strip_reasoning(reply).split())[:300]) or None


def _term_pattern(term: str) -> re.Pattern[str]:
    if _CJK.search(term):
        return re.compile(re.escape(term))
    return re.compile(r"(?<!\w)" + re.escape(term) + r"(?!\w)")


@cache
def _terms_pattern(terms: tuple[str, ...]) -> re.Pattern[str]:
    longest_first = sorted(terms, key=len, reverse=True)
    return re.compile("|".join(_term_pattern(term).pattern for term in longest_first))


def _mentions(text: str, terms: tuple[str, ...]) -> bool:
    """Is one of `terms` written in `text`? Both are compared casefolded."""
    return bool(terms) and _terms_pattern(terms).search(text) is not None


def _is_short_question(text: str, taken_out: tuple[str, ...]) -> bool:
    """Is little left of `text` once the terms in `taken_out` are removed?"""
    rest = _terms_pattern(taken_out).sub(" ", text) if taken_out else text
    cjk = len(_CJK.findall(rest))
    latin = len(_LATIN_WORD.findall(_CJK.sub(" ", rest)))
    return latin <= MAX_OTHER_LATIN_WORDS and cjk <= MAX_OTHER_CJK_CHARS


def bus_routes_named(text: str) -> list[str]:
    """Bus routes written in `text`, in the order they appear."""
    found = []
    for match in _ROUTE.finditer(text):
        route = match.group(1).upper()
        route = "9025A" if route == "9025" else route
        if route in tdx.ROUTES and route not in found:
            found.append(route)
    return found


def destinations_named(text: str) -> list[destinations.DestinationResponse]:
    return [
        destination
        for destination in destinations.load()
        if _mentions(text, DESTINATION_WORDS.get(destination.id, ()))
    ]


def bus_question(
    text: str,
) -> tuple[list[str], list[destinations.DestinationResponse]] | None:
    """The routes and destinations a bus question is about; None if it is not one.

    `text` is casefolded. A number such as 132 is only a route beside a bus
    word ("the fee is 132" is not about a bus), and a destination only counts
    beside a bus word or a how-do-I-get-there phrase ("extend my ARC at the
    immigration office" is not about getting there).
    """
    about_bus = _mentions(text, BUS_WORDS)
    if not about_bus and not _mentions(text, WAY_WORDS):
        return None
    wanted = destinations_named(text)
    routes = bus_routes_named(text) if about_bus else []
    if not about_bus and not wanted:
        return None
    for destination in wanted:
        routes += [route for route in destination.bus_routes if route not in routes]
    if not routes and not wanted:
        routes = list(DEFAULT_BUS_ROUTES)
    return routes, wanted


def places_by_code(text: str) -> list[campus_places.CampusPlace]:
    """Buildings whose code is written in `text` (in its original case).

    A code of letters only ("LH", "TR") has to be typed in capitals, and a
    one-letter code ("A", "S") also needs its room ("A-203"); otherwise any
    "a" or "is" in a sentence would be a building.
    """
    by_code = {place.code: place for place in campus_places.load().places if place.code}
    found: list[campus_places.CampusPlace] = []
    for match in _CODE.finditer(text):
        written, room = match.group(1), match.group(2)
        place = by_code.get(written.upper())
        if place is None or place in found:
            continue
        has_digit = any(char.isdigit() for char in written)
        if (len(written) == 1 and not room) or (
            not has_digit and not written.isupper()
        ):
            continue
        found.append(place)
    return found


@cache
def _place_names() -> list[tuple[campus_places.CampusPlace, tuple[str, ...]]]:
    """Every building with the ways a question may write its name, casefolded."""
    named = []
    for place in campus_places.load().places:
        if place.category == "transit":
            continue  # the bus stops are answered with live arrivals instead
        names: set[str] = set()
        for name in (place.name_zh, place.name_en, *place.aliases):
            # "Zong-Jiao Hall (Language Center)" is asked for by either part.
            for part in re.split(r"[()（）]", name.casefold()):
                part = part.strip()
                for prefix in _NAME_PREFIXES:
                    part = part.removeprefix(prefix)
                if len(part) >= 2:
                    names.add(part)
        named.append((place, tuple(sorted(names))))
    return named


def place_question(text: str) -> list[campus_places.CampusPlace]:
    """The buildings a "where is ..." question asks for; empty if it is not one.

    `text` is in its original case. Besides the building (by code, or by name
    with a where-word) the question may hold only a few other words.
    """
    folded = text.casefold()
    coded = places_by_code(text)
    if coded:
        codes = tuple(
            match.group(0).casefold()
            for match in _CODE.finditer(text)
            if match.group(1).upper() in {place.code for place in coded}
        )
        if _is_short_question(folded, (*codes, *WHERE_WORDS, "room", "教室", "phòng")):
            return coded
        return []
    if not _mentions(folded, WHERE_WORDS):
        return []
    hits: list[tuple[int, int, campus_places.CampusPlace]] = []
    written: list[str] = []
    for index, (place, names) in enumerate(_place_names()):
        matched = [name for name in names if _mentions(folded, (name,))]
        if matched:
            hits.append((-max(map(len, matched)), index, place))
            written += matched
    if not hits or not _is_short_question(folded, (*written, *WHERE_WORDS)):
        return []
    return [place for _, _, place in sorted(hits, key=lambda hit: hit[:2])]


def place_passage(place: campus_places.CampusPlace) -> dict[str, Any]:
    lines = [f"{place.name_en} ({place.name_zh}) is on the NCU campus."]
    if place.code:
        lines.append(
            f"Building code: {place.code}. Room numbers that begin with "
            f"{place.code}- are in this building."
        )
    if place.aliases:
        lines.append("Also called: " + ", ".join(place.aliases) + ".")
    if place.note_en:
        lines.append(place.note_en)
    return {
        "id": place.id,
        "title": f"{place.name_en} ({place.name_zh})",
        "url": place.map_links.google_walking_directions,
        "fetched_on": None,
        "passage": "\n".join(lines),
    }


def destination_passage(
    destination: destinations.DestinationResponse,
) -> dict[str, Any]:
    lines = [destination.getting_there.en]
    if destination.address:
        lines.append(f"Address: {destination.address.en}")
    if destination.hours:
        lines.append(f"Hours: {destination.hours.en}")
    return {
        "id": destination.id,
        "title": f"{destination.name.en} ({destination.name.zh})",
        "url": destination.maps_url,
        "fetched_on": destination.checked_on.isoformat(),
        "passage": "\n".join(lines),
    }


def soonest_per_stop(stops: list[tdx.StopArrival]) -> list[tdx.StopArrival]:
    """One row per stop name: the pass of the bus that comes first.

    A loop route such as 132 passes the same campus stop twice, and the two
    directions of 172 each list it once. A student asking when the bus comes
    wants the next one, not two rows with the same name.
    """

    def wait(stop: tdx.StopArrival) -> tuple[int, float]:
        if stop.estimate_seconds is not None:
            return (0, stop.estimate_seconds)
        if stop.next_bus_time is not None:
            return (1, stop.next_bus_time.timestamp())
        return (2, 0.0)

    best: dict[str, tdx.StopArrival] = {}
    for stop in stops:
        kept = best.get(stop.stop_name.zh)
        if kept is None or wait(stop) < wait(kept):
            best[stop.stop_name.zh] = stop
    return list(best.values())


def bus_arrivals(
    routes: list[str],
) -> tuple[list[dict[str, Any]], BusProblem | None]:
    """Live arrivals at the campus stops for each route, and what went wrong."""
    arrivals: list[dict[str, Any]] = []
    for route in routes[:MAX_BUS_ROUTES]:
        try:
            stops = tdx.tdx_client.arrivals_with_positions(route, True)
        except tdx.TdxNotConfiguredError:
            return arrivals, "not_configured"
        except tdx.TdxRateLimitedError:
            return arrivals, "rate_limited"
        except tdx.TdxError as error:
            logger.warning(f"TDX request for route {route} failed: {error}")
            return arrivals, "unreachable"
        arrivals.append(
            {
                "route": route,
                "fetched_at": datetime.now(UTC),
                "stops": soonest_per_stop(stops),
            }
        )
    return arrivals, None


def check_answer(answer: str, question: str, passages: list[dict]) -> dict[str, Any]:
    """What a reader can verify without knowing the subject."""
    context = "\n".join(p["title"] + "\n" + p["passage"] for p in passages)
    cited = cited_numbers(answer)
    return {
        "refused": is_refusal(answer),
        "cited": sorted(set(cited)),
        "citations_valid": bool(cited) and all(1 <= n <= len(passages) for n in cited),
        "unsupported_numbers": unsupported_numbers(
            answer, context + "\n" + question, ignore=OIA_CONTACT_STRINGS
        ),
    }


def look_up(question: str, query: str, result: dict[str, Any]) -> None:
    """Fill `result` with what the backend knows about the question.

    For a bus or a place, the question and its translation are each read on
    their own: a word of one never combines with a word of the other. The
    documents are searched for the words of both, so a translation that
    misses the office's term does not hide what the question itself matches
    (a Chinese document, or a term of the search's glossary).
    """
    texts = list(dict.fromkeys((question, query)))

    for text in texts:
        bus = bus_question(text.casefold())
        if bus is None:
            continue
        routes, wanted = bus
        result["kind"] = "bus"
        result["destinations"] = wanted
        result["bus"], result["bus_problem"] = bus_arrivals(routes)
        # Only the written directions go to the model. The live arrivals are
        # returned as data for the client to show: asked to put them into
        # words, an untuned 2B model gave the time the data was read as the
        # time of the bus, and answered Vietnamese questions in Thai script.
        result["passages"] = [destination_passage(d) for d in wanted]
        return

    for text in texts:
        places = place_question(text)
        if places:
            result["kind"] = "place"
            result["places"] = places[:MAX_PLACES]
            result["passages"] = [place_passage(place) for place in result["places"]]
            return

    result["passages"] = [
        {key: row[key] for key in ("id", "title", "url", "fetched_on", "passage")}
        for row in student_services.search(" ".join(reversed(texts)), limit=TOP_K)
    ]


def ask(question: str, model: Any = None) -> dict[str, Any]:
    """Look the question up and, with a model, answer it.

    `model` needs `.chat(messages, max_tokens=...)` and `.name`.
    """
    question = question.strip()[:MAX_QUESTION_CHARS]
    result: dict[str, Any] = {
        "question": question,
        "kind": "documents",
        "search_query": question,
        "translated": False,
        "passages": [],
        "places": [],
        "destinations": [],
        "bus": [],
        "bus_problem": None,
        "model": getattr(model, "name", None),
        "answer": None,
        "checks": None,
        "error": None,
        "seconds": None,
    }
    if not question:
        result["error"] = "empty question"
        return result
    started = time.monotonic()
    try:
        translation = english_query(question, model)
    except ModelError as error:
        # Still worth looking up: codes and route numbers need no translation.
        translation, result["error"] = None, f"model endpoint: {error}"
    if translation:
        result["search_query"], result["translated"] = translation, True
    look_up(question, result["search_query"], result)
    # With nothing to read (a bus question about arrivals only) the model
    # could only refuse or guess, so it is not asked.
    readable = result["passages"] or result["kind"] == "documents"
    if model is not None and readable and result["error"] is None:
        try:
            answer = strip_reasoning(
                model.chat(build_messages(question, result["passages"]))
            )
        except ModelError as error:
            result["error"] = f"model endpoint: {error}"
        else:
            if answer:
                result["answer"] = answer
                result["checks"] = check_answer(answer, question, result["passages"])
            else:  # only thinking came back, or nothing at all
                result["error"] = "model endpoint: empty answer"
    result["seconds"] = round(time.monotonic() - started, 1)
    return result
