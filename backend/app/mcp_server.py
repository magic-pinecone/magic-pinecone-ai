"""MCP server for the international-student data.

Lets an AI client look up NCU's graduation language requirements instead of
recalling them. The tools only read the curated data and the corpus in
`app/data/graduation_language/`; numbers come from there, never from the model.

    uv run python -m app.mcp_server        # stdio transport

Example client entry:

    {"command": "uv", "args": ["run", "python", "-m", "app.mcp_server"], "cwd": "backend"}
"""

import re
from typing import Any, Literal

from mcp.server.mcpserver import MCPServer

from app.services import graduation_language as service

mcp = MCPServer(
    "ncu-international",
    instructions=(
        "Official language requirements for graduating from National Central "
        "University (NCU), for international students. Quote thresholds exactly "
        "as returned and cite the source URLs. When no rule is returned, say the "
        "department's own regulations must be checked; do not infer a threshold."
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


if __name__ == "__main__":
    mcp.run()
