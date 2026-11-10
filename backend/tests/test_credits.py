import re
import unicodedata
from pathlib import Path
from urllib.parse import urlparse

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import credits

URL = "/api/v1/intl/credits"
CORPUS = (
    Path(__file__).resolve().parent.parent
    / "app"
    / "data"
    / "graduation_language"
    / "corpus"
)


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def ids(response) -> list[str]:
    return [requirement["id"] for requirement in response.json()["requirements"]]


def all_sources() -> list[credits.Source]:
    data = credits.load()
    sources = [*data.contact.sources]
    for requirement in data.requirements:
        sources.extend(requirement.sources)
    return sources


def squash(text: str) -> str:
    # PDF extraction emits CJK compatibility ideographs (U+F967 for 不); NFKC
    # folds them into the characters a reader types.
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text))


def corpus_texts() -> dict[str, str]:
    """Official text of each corpus document, keyed by its source URL."""
    texts = {}
    for path in CORPUS.glob("*.md"):
        _, header, body = path.read_text(encoding="utf-8").split("---", 2)
        url = re.search(r'^url: "(.*)"$', header, re.MULTILINE)
        if url:
            texts[url.group(1)] = squash(body)
    return texts


def test_every_source_is_an_official_ncu_page_with_a_quote():
    sources = all_sources()
    assert sources
    for source in sources:
        host = urlparse(source.url).hostname or ""
        assert host.endswith(".ncu.edu.tw"), source.url
        assert source.quote.strip(), source.url


def test_quotes_from_corpus_documents_are_verbatim():
    texts = corpus_texts()
    checked = [s for s in all_sources() if s.url in texts]
    assert checked
    for source in checked:
        assert squash(source.quote) in texts[source.url], source.quote


def test_every_requirement_has_both_languages():
    for requirement in credits.load().requirements:
        assert requirement.title_zh.strip() and requirement.title_en.strip()
        assert requirement.summary_zh.strip() and requirement.summary_en.strip()


def test_returns_every_requirement_with_credits_contact_and_open_questions(
    client: TestClient,
):
    response = client.get(URL)
    assert response.status_code == 200
    body = response.json()
    assert body["contact"]["name_zh"] == "教務處課務組"
    assert body["open_questions"]
    by_id = {r["id"]: r for r in body["requirements"]}
    assert len(by_id) == len(credits.load().requirements)
    assert by_id["minimum-credits"]["credits"] == 128
    assert by_id["mandarin-substitution"]["credits"] == 6
    assert by_id["curriculum-by-cohort"]["credits"] is None


def test_bachelor_filter_keeps_the_undergraduate_structure(client: TestClient):
    bachelor = ids(client.get(URL, params={"degree": "bachelor"}))
    assert {
        "minimum-credits",
        "common-required-total",
        "chinese",
        "mandarin-substitution",
        "general-education",
    } <= set(bachelor)
    assert "master-minimum-credits" not in bachelor


def test_graduate_filter_only_keeps_graduate_minimums(client: TestClient):
    assert ids(client.get(URL, params={"degree": "graduate"})) == [
        "master-minimum-credits",
        "doctoral-minimum-credits",
        "direct-doctoral-minimum-credits",
    ]


def test_unknown_degree_is_rejected(client: TestClient):
    assert client.get(URL, params={"degree": "phd"}).status_code == 422
