import json

import pytest
from fastapi.testclient import TestClient

from app.api.routes import intl
from app.main import app
from app.services import gov_guides


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def test_every_page_source_has_its_text_and_services_have_none():
    sources = json.loads(
        (gov_guides.DATA_DIR / "sources.json").read_text(encoding="utf-8")
    )["sources"]
    articles = {a["id"]: a for a in gov_guides.load_articles()}
    assert set(articles) == {s["id"] for s in sources}
    for source in sources:
        article = articles[source["id"]]
        if source["extract"] == "link":
            assert article["is_service"] and article["body"] == ""
        else:
            assert article["body"] and article["fetched_on"], source["id"]
            assert not article["body"].startswith("# ")


def test_articles_are_grouped_by_topic_in_display_order():
    topics = [a["topic"] for a in gov_guides.load_articles()]
    assert topics == sorted(topics, key=gov_guides.TOPIC_ORDER.index)
    assert gov_guides.topics() == list(dict.fromkeys(topics))


def test_guides_endpoint_filters_by_topic(client):
    everything = client.get("/api/v1/intl/guides").json()
    assert everything["topics"] == gov_guides.topics()
    work = client.get("/api/v1/intl/guides", params={"topic": "work"}).json()
    assert work["articles"] and all(a["topic"] == "work" for a in work["articles"])
    assert len(work["articles"]) < len(everything["articles"])


def test_guides_endpoint_rejects_an_unknown_topic(client):
    assert (
        client.get("/api/v1/intl/guides", params={"topic": "visa"}).status_code == 422
    )


def fake_translate(texts):
    # Translates everything but the line "skip", as a translator that failed on it would.
    return {t: f"[vi] {t}" for t in texts if t != "skip"}


def test_localize_translates_only_guides_in_another_language():
    articles = [
        {
            "id": "zh",
            "lang": "zh",
            "title": "標題",
            "org": "勞動部",
            "body": "一\nskip",
        },
        {"id": "en", "lang": "en", "title": "Title", "org": "NIA", "body": "One"},
    ]
    zh, en = gov_guides.localize(articles, "vi", fake_translate)
    assert zh["title"] == "[vi] 標題" and zh["org"] == "[vi] 勞動部"
    assert zh["body"] == "[vi] 一\nskip"  # an untranslated line stays as written
    assert zh["original"] == {
        "lang": "zh",
        "title": "標題",
        "org": "勞動部",
        "body": "一\nskip",
    }
    assert en["title"] == "[vi] Title"

    zh, en = gov_guides.localize(articles, "zh-TW", fake_translate)
    assert zh["original"] is None and zh["title"] == "標題"
    assert en["original"]["title"] == "Title"
    zh, en = gov_guides.localize(articles, "en-US", fake_translate)
    assert en["original"] is None and zh["original"] is not None


def test_guides_endpoint_translates_with_lang(client, monkeypatch):
    monkeypatch.setattr(
        intl, "translate", lambda _session, texts, _lang: fake_translate(texts)
    )
    articles = client.get("/api/v1/intl/guides", params={"lang": "vi"}).json()[
        "articles"
    ]
    assert all(a["original"] for a in articles)
    assert all(a["title"].startswith("[vi] ") for a in articles)
    plain = client.get("/api/v1/intl/guides").json()["articles"]
    assert all(a["original"] is None for a in plain)


def test_guides_endpoint_rejects_a_malformed_lang(client):
    response = client.get("/api/v1/intl/guides", params={"lang": "vi;drop"})
    assert response.status_code == 422
