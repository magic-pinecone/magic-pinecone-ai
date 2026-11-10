"""Central-government guides for international students, read from `app/data/gov_guides/`.

Each source in `sources.json` is an official page (or a news report) on work
permits, insurance or residence. Pages are served with their text as of the
`fetched_on` day; online services (`"extract": "link"`) have no text, only a link.
"""

import json
import re
from functools import lru_cache
from pathlib import Path
from typing import Any

DATA_DIR = Path(__file__).resolve().parent.parent / "data/gov_guides"
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
# The display order of topics; a topic missing here sorts last.
TOPIC_ORDER = ("work", "insurance", "residence", "help")


def _article(source: dict[str, Any]) -> dict[str, Any]:
    path = DATA_DIR / "corpus" / f"{source['id']}.md"
    fetched_on, body = "", ""
    if path.exists():
        text = path.read_text(encoding="utf-8")
        head = FRONT_MATTER.match(text)
        fetched = (
            re.search(r'^fetched_on: "(.*)"$', head.group(1), re.M) if head else None
        )
        fetched_on = fetched.group(1) if fetched else ""
        # Drop the front matter and the "# title" line render() puts above the text.
        body = FRONT_MATTER.sub("", text).strip().removeprefix(f"# {source['title']}")
    return {
        "id": source["id"],
        "title": source["title"],
        "org": source["org"],
        "lang": source["lang"],
        "topic": source["topic"],
        "url": source["url"],
        "is_service": source.get("extract") == "link",
        "fetched_on": fetched_on,
        "body": body.strip(),
    }


@lru_cache
def load_articles() -> tuple[dict[str, Any], ...]:
    sources = json.loads((DATA_DIR / "sources.json").read_text(encoding="utf-8"))
    articles = [_article(source) for source in sources["sources"]]
    rank = {topic: index for index, topic in enumerate(TOPIC_ORDER)}
    # sorted() is stable, so articles keep their sources.json order within a topic.
    return tuple(sorted(articles, key=lambda a: rank.get(a["topic"], len(rank))))


def topics() -> list[str]:
    return list(dict.fromkeys(article["topic"] for article in load_articles()))
