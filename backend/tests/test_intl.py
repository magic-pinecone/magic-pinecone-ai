import json

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import graduation_language as service

URL = "/api/v1/intl/graduation-language"


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def test_graduation_language_matches_app_contract(client: TestClient):
    body = client.get(URL).json()

    assert set(body) == {
        "verified_on",
        "disclaimer",
        "tests",
        "general",
        "chinese",
        "rules",
        "sources",
    }
    rule = next(r for r in body["rules"] if r["id"] == "bachelor-management")
    assert rule["degree"] == "bachelor"
    assert rule["college"] == {"zh": "管理學院", "en": "College of Management"}
    assert rule["english"]["toeic_lr"] == [
        {"value": "700", "cohort_min": None, "cohort_max": None, "note": None}
    ]
    assert rule["sources"] == [
        "lc-threshold-chart",
        "lc-form-general",
        "aa-english-proficiency-rules",
    ]


def test_cohort_selects_the_threshold_of_the_admission_year(client: TestClient):
    def toeic(cohort: int) -> list[str]:
        params = {"college": "資訊電機學院", "cohort": cohort}
        (rule,) = client.get(URL, params=params).json()["rules"]
        return [t["value"] for t in rule["english"]["toeic_lr"]]

    assert toeic(114) == ["600"]
    assert toeic(115) == ["785（聽力 400、閱讀 385）"]


def test_college_matches_id_and_either_name(client: TestClient):
    for college in ("bachelor-earth", "地球科學學院", "college of earth sciences"):
        rules = client.get(URL, params={"college": college}).json()["rules"]
        assert [r["id"] for r in rules] == ["bachelor-earth"]


def test_response_lists_only_the_sources_it_cites(client: TestClient):
    body = client.get(URL, params={"college": "工學院"}).json()

    cited = {
        *body["general"]["sources"],
        *body["chinese"]["sources"],
        *body["rules"][0]["sources"],
    }
    assert {s["id"] for s in body["sources"]} == cited


def test_unknown_college_returns_no_rules(client: TestClient):
    assert client.get(URL, params={"college": "不存在"}).json()["rules"] == []


def test_every_rule_cites_known_sources_and_known_tests():
    data = service.load_requirements()
    source_ids = {s["id"] for s in service.load_sources()}

    for rule in data["rules"]:
        assert set(rule["sources"]) <= source_ids, rule["id"]
        assert set(rule["english"]) <= set(data["tests"]), rule["id"]
    assert set(data["general"]["sources"]) <= source_ids
    assert set(data["chinese"]["sources"]) <= source_ids


def test_every_source_has_a_corpus_file_with_its_metadata():
    for source in service.load_sources():
        text = (service.DATA_DIR / "corpus" / f"{source['id']}.md").read_text("utf-8")
        assert f"url: {json.dumps(source['url'], ensure_ascii=False)}" in text
        assert "\nsha256: " in text


def test_department_rule_names_its_department(client: TestClient):
    rules = client.get(URL, params={"college": "文學院"}).json()["rules"]

    french = next(r for r in rules if r["id"] == "bachelor-french")
    assert french["department"]["zh"] == "法國語文學系"
    assert french["other_languages"] == [
        {"language": "法文", "test": "法語鑑定文憑 DELF", "requirement": "B1"}
    ]


def test_programs_match_app_contract(client: TestClient):
    body = client.get("/api/v1/intl/programs").json()

    assert set(body) == {"intake", "source_url", "fetched_on", "programs"}
    assert set(body["programs"][0]) == {
        "name",
        "degree",
        "teaching_language",
        "college",
        "admission_language_note",
        "graduation",
        "regulation_url",
        "website",
    }
    assert set(body["programs"][0]["graduation"]) == {
        "years",
        "credits",
        "elective_credits",
        "required_credits",
    }


def test_programs_filter_by_degree_language_and_name(client: TestClient):
    params = {"degree": "master", "teaching_language": "english", "q": "國際經營"}
    programs = client.get("/api/v1/intl/programs", params=params).json()["programs"]

    assert programs
    for program in programs:
        assert program["degree"] == "master"
        assert program["teaching_language"] == "english"
        assert "國際經營" in program["name"]["zh"]
