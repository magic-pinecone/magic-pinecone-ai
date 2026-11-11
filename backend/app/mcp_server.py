"""MCP server for the international-student data.

Lets an AI client look things up instead of recalling them: NCU's graduation
language requirements, the Office of International Affairs pages on insurance,
immigration and work permits, whom to contact, when National Health Insurance
starts, where a building is, when the next campus bus comes and how to reach
places off campus. The tools read the curated data under `app/data/`, and bus
arrivals come live from TDX; numbers, dates and times come from there, never
from the model.

    uv run python -m app.mcp_server        # stdio transport

Example client entry:

    {"command": "uv", "args": ["run", "python", "-m", "app.mcp_server"], "cwd": "backend"}
"""

import re
from datetime import UTC, date, datetime
from typing import Any, Literal

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError

from app.services import campus_places, destinations, student_services, tdx
from app.services import graduation_language as service
from app.services.campus_places import PlaceCategory
from app.services.destinations import DestinationCategory

mcp = MCPServer(
    "ncu-international",
    instructions=(
        "Official information for international students at National Central "
        "University (NCU): graduation language requirements, insurance, "
        "immigration and work-permit rules from the Office of International "
        "Affairs (OIA), contacts, campus places, live arrivals of the campus "
        "buses and off-campus destinations. Quote thresholds, amounts, dates "
        "and times exactly as returned and cite the source URLs. Search in "
        "English or Chinese whatever language the student writes in, then "
        "answer in the student's language. When a tool returns nothing, say so "
        "and give the OIA contact; do not infer a rule. For overstayed visas, "
        "working without a permit, medical emergencies or personal crises, "
        "refer the student to OIA or emergency services instead of advising."
    ),
)

Degree = Literal["bachelor", "master", "phd"]
TeachingLanguage = Literal["chinese", "english_sufficient", "english"]
SNIPPET_RADIUS = 160
FRONT_MATTER = re.compile(r"\A---\n.*?\n---\n", re.DOTALL)


@mcp.tool()
def get_graduation_language_requirements(
    degree: Degree, college: str | None = None, cohort: int | None = None
) -> dict[str, Any]:
    """Language requirements for graduating from NCU.

    Args:
        degree: bachelor, master or phd.
        college: a college's Chinese or English name (e.g. "工學院",
            "College of Management"); omit for every college.
        cohort: admission academic year in the ROC calendar (e.g. 115 for
            2026); thresholds differ by admission year.

    Returns the matching rules with accepted tests and scores, alternatives,
    clauses about international students, and the official sources. `en` holds
    unofficial English renderings of the Chinese strings.
    """
    data = service.load_requirements()
    rules = service.find_rules(degree=degree, college=college, cohort=cohort)
    cited = {source for rule in rules for source in rule["sources"]}
    english = service.load_english()
    shown = service.display_strings({**data, "rules": rules})
    overview = (
        {key: data["general"][key] for key in ("summary", "procedure", "alternatives")}
        | {"international_students": data["general"]["international_students"]}
        if degree == "bachelor"
        else data["general"]["graduate"]
    )
    return {
        "verified_on": data["verified_on"],
        "disclaimer": data["disclaimer"],
        "tests": data["tests"],
        "overview": overview,
        "rules": rules,
        "sources": [s for s in service.load_sources() if s["id"] in cited],
        "en": {text: english[text] for text in shown if text in english},
    }


@mcp.tool()
def get_chinese_language_requirements() -> dict[str, Any]:
    """Chinese-language (華語) rules at NCU that bear on graduation.

    Also lists rules often mistaken for graduation requirements (scholarship
    and admission conditions) and questions still open with the university.
    """
    data = service.load_requirements()
    chinese = data["chinese"]
    return {
        "verified_on": data["verified_on"],
        **chinese,
        "sources": [s for s in service.load_sources() if s["id"] in chinese["sources"]],
    }


@mcp.tool()
def search_international_programs(
    query: str | None = None,
    degree: Degree | None = None,
    teaching_language: TeachingLanguage | None = None,
) -> dict[str, Any]:
    """Programs NCU opens to international degree students.

    Args:
        query: part of a program or college name, Chinese or English.
        degree: bachelor, master or phd.
        teaching_language: chinese, english_sufficient (enough English-taught
            courses to graduate) or english (taught entirely in English).

    Each program has its admission language proof, graduation credits and a
    link to the department's own graduation rules.
    """
    data = service.load_programs()
    programs = service.find_programs(
        degree=degree, teaching_language=teaching_language, query=query
    )
    return {**data, "programs": programs}


@mcp.tool()
def search_official_documents(query: str, limit: int = 5) -> list[dict[str, str]]:
    """Search the full text of NCU's official documents on language requirements.

    Args:
        query: words to look for, e.g. "外籍生 華語" or "TOEIC 進修英文". Every
            word must appear in a document for it to match.
        limit: how many documents to return.

    Returns the best matches with a snippet and the official URL. Use it for
    details the structured tools do not cover, and quote the text as written.
    """
    words = [word.casefold() for word in query.split() if word]
    if not words:
        return []
    matches = []
    for source in service.load_sources():
        path = service.DATA_DIR / "corpus" / f"{source['id']}.md"
        body = FRONT_MATTER.sub("", path.read_text(encoding="utf-8"))
        folded = body.casefold()
        counts = [folded.count(word) for word in words]
        if not all(counts):
            continue
        at = folded.find(words[0])
        snippet = body[max(0, at - SNIPPET_RADIUS) : at + SNIPPET_RADIUS]
        matches.append(
            (
                sum(counts),
                {
                    "id": source["id"],
                    "title": source["title"],
                    "org": source["org"],
                    "effective": source.get("effective", ""),
                    "url": source["url"],
                    "snippet": " ".join(snippet.split()),
                },
            )
        )
    matches.sort(key=lambda match: match[0], reverse=True)
    return [document for _, document in matches[: max(1, limit)]]


@mcp.resource("ncu-intl://documents/{source_id}")
def official_document(source_id: str) -> str:
    """The full text of one official document, by its source id."""
    if source_id not in {source["id"] for source in service.load_sources()}:
        raise ValueError(f"unknown document: {source_id}")
    path = service.DATA_DIR / "corpus" / f"{source_id}.md"
    return path.read_text(encoding="utf-8")


@mcp.tool()
def search_student_services(
    query: str, topic: str | None = None, limit: int = 4
) -> dict[str, Any]:
    """Search NCU's OIA pages on everyday administrative matters.

    Covers medical insurance (Cathay insurance, National Health Insurance,
    student group insurance), immigration forms (visa, ARC), work permits,
    scholarships, and information for incoming degree and exchange students.

    Args:
        query: a few English keywords, e.g. "work permit hours per week" or
            "ARC extension form". The pages are in English, so translate the
            student's question first.
        topic: insurance, immigration, work_permit, exchange or general, to
            search one area only.
        limit: how many passages to return.

    Returns passages with their page title, URL and the date the page was
    fetched. Answer only from the passages and cite the URL. An empty list
    means the pages do not cover the question: say so and give the contact
    from `get_student_service_contact`.
    """
    return {
        "topics": student_services.topics(),
        "passages": student_services.search(query, topic=topic, limit=limit),
    }


@mcp.tool()
def get_student_service_contact(topic: str | None = None) -> dict[str, Any]:
    """Which NCU office an international student should contact, and how.

    Args:
        topic: insurance, work_permit, immigration, degree_students, exchange,
            overseas_chinese_students, visiting_programs, admissions or
            general; omit for every office.

    Each office has a direct phone number, an extension (dialled after the
    switchboard number), its location with map links, and opening hours.
    Staff names are not included; `staff_page` lists who holds each duty.
    """
    contacts = student_services.load_contacts()
    offices = []
    for office in student_services.find_contacts(topic):
        place = campus_places.get(office["place_id"])
        offices.append({**office, "place": place.model_dump() if place else None})
    return {
        **{key: contacts[key] for key in ("verified_on", "switchboard", "staff_page")},
        "topics": student_services.contact_topics(),
        "offices": offices,
    }


@mcp.tool()
def estimate_nhi_enrollment_date(
    arrival_date: str, trips_abroad: list[list[str]] | None = None
) -> dict[str, Any]:
    """When a newly arrived student can enrol in National Health Insurance (NHI).

    Always use this instead of working the date out yourself.

    Args:
        arrival_date: first arrival in Taiwan, as YYYY-MM-DD.
        trips_abroad: journeys out of Taiwan since then, each as
            [departure, re-entry] in YYYY-MM-DD.

    Applies the rule on the OIA insurance page: six months of continuous
    residence, one trip under 30 days postpones the date, a longer or second
    trip restarts the count. Returns the date, the last day private insurance
    is needed, each step, the assumptions made and the source. Present it as
    an estimate to confirm with OIA.
    """
    try:
        trips = [
            (date.fromisoformat(departure), date.fromisoformat(reentry))
            for departure, reentry in trips_abroad or []
        ]
        return student_services.nhi_enrollment(date.fromisoformat(arrival_date), trips)
    except ValueError as error:
        # ToolError's text reaches the client, so it can ask the student again.
        raise ToolError(f"Check the dates (YYYY-MM-DD): {error}") from error


@mcp.tool()
def find_place(query: str = "", category: PlaceCategory | None = None) -> dict:
    """Find buildings and resources on the NCU campus and how to get to them.

    `query` matches a Chinese or English name, a building code such as "E6",
    an alias such as "OIA", or a unit housed in a building, such as "資工系" or
    "諮商中心"; leave it empty to list a whole category. The closest matches
    come first. A building's `units` lists the colleges, departments, offices
    and centers inside it, with the floor when known. Each result has coordinates and links that
    open walking directions in Google or Apple Maps. An empty result means the
    place is not in the list, not that it does not exist. Show the attribution
    when you show the results.
    """
    return {
        "attribution": campus_places.load().attribution,
        "places": [
            place.model_dump() for place in campus_places.search(query, category)
        ],
    }


@mcp.tool()
def get_bus_arrivals(route: str, campus_only: bool = True) -> dict[str, Any]:
    """Live estimated arrivals of a bus route at the NCU campus stops.

    Args:
        route: 132 or 133 (Zhongli), 172 or 173 (THSR Taoyuan Station), or
            9025A (Taipei).
        campus_only: false to get every stop of the route, not only those on
            campus.

    Each stop has a `status`. EN_ROUTE: a bus is running and reaches the stop
    in `estimate_seconds`. NOT_DEPARTED: no bus is on the way yet and
    `next_bus_time` is the next scheduled departure. LAST_BUS_GONE and
    NO_SERVICE: no more buses today. `sequence` orders the stops along the
    route; a loop route passes the same stop name twice. The data comes from
    TDX and is at most half a minute old; give `fetched_at` with the times.

    When this tool fails, because the server has no TDX key, TDX is rate
    limiting or TDX cannot be reached, tell the student that live arrivals
    are unavailable right now and why. Never guess or estimate a bus time.
    """
    if route not in tdx.ROUTES:
        raise ToolError(
            f"Unknown bus route {route!r}. Available routes: {', '.join(tdx.ROUTES)}"
        )
    try:
        stops = tdx.tdx_client.arrivals_with_positions(route, campus_only)
    except tdx.TdxNotConfiguredError as error:
        raise ToolError(
            "Live bus arrivals are unavailable: this server has no TDX API key "
            "configured. Do not guess arrival times."
        ) from error
    except tdx.TdxRateLimitedError as error:
        raise ToolError(
            "Live bus arrivals are unavailable: TDX is rate limiting this "
            f"server. Try again in about {error.retry_after} seconds. Do not "
            "guess arrival times."
        ) from error
    except tdx.TdxError as error:
        raise ToolError(
            "Live bus arrivals are unavailable: TDX could not be reached or "
            "sent an unusable answer. Do not guess arrival times."
        ) from error
    return {
        "route": route,
        "fetched_at": datetime.now(UTC).isoformat(),
        "source": "TDX (tdx.transportdata.tw)",
        "stops": [stop.model_dump(mode="json") for stop in stops],
    }


@mcp.tool()
def find_destination(
    query: str = "", category: DestinationCategory | None = None
) -> dict[str, Any]:
    """Find places off campus that students need to reach, and how to get there.

    Covers the Zhongli train station, the THSR (high-speed rail) Taoyuan
    station, Taoyuan airport, the immigration office (ARC) and the National
    Health Insurance office. For buildings on campus use `find_place`.

    `query` matches a Chinese or English name or address, e.g. "airport",
    "高鐵" or "immigration"; leave it empty to list a whole category or
    everything. Each result has `getting_there` in Chinese and English, the
    bus routes to take (their live arrivals come from `get_bus_arrivals`), a
    `maps_url` that opens public-transport directions from NCU, and the
    sources with the date they were checked. Address, phone and hours can be
    null: say they are not on file instead of filling them in. An empty result
    means the place is not in the list, not that it does not exist.
    """
    return {
        "destinations": [
            destination.model_dump(mode="json")
            for destination in destinations.search(query, category)
        ]
    }


if __name__ == "__main__":
    mcp.run()
