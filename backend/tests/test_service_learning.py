from urllib.parse import urlparse

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import service_learning

URL = "/api/v1/intl/service-learning"


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def ids(response) -> list[str]:
    return [requirement["id"] for requirement in response.json()["requirements"]]


def test_every_source_is_an_official_ncu_page_with_a_quote():
    data = service_learning.load()
    sources = [*data.contact.sources]
    for requirement in data.requirements:
        sources.extend(requirement.sources)
    assert sources
    for source in sources:
        host = urlparse(source.url).hostname or ""
        assert host.endswith(".ncu.edu.tw"), source.url
        assert source.quote.strip(), source.url


def test_every_requirement_has_both_languages():
    for requirement in service_learning.load().requirements:
        assert requirement.title_zh.strip() and requirement.title_en.strip()
        assert requirement.summary_zh.strip() and requirement.summary_en.strip()


def test_returns_every_requirement_with_contact_and_open_questions(
    client: TestClient,
):
    response = client.get(URL)
    assert response.status_code == 200
    body = response.json()
    assert body["contact"]["name_zh"] == "學務處服務學習發展中心"
    assert body["open_questions"]
    assert len(body["requirements"]) == len(service_learning.load().requirements)


def test_bachelor_filter_keeps_the_course_and_the_100_hours(client: TestClient):
    bachelor = ids(client.get(URL, params={"degree": "bachelor"}))
    assert {"course", "graduation-condition", "passport-100-hours"} <= set(bachelor)
    assert "graduate-not-listed" not in bachelor


def test_graduate_filter_only_keeps_graduate_rules(client: TestClient):
    assert ids(client.get(URL, params={"degree": "graduate"})) == [
        "graduate-not-listed"
    ]


def test_unknown_degree_is_rejected(client: TestClient):
    assert client.get(URL, params={"degree": "phd"}).status_code == 422
