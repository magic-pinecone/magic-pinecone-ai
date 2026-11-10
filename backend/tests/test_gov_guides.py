import json

import pytest
from fastapi.testclient import TestClient

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
