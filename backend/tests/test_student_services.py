import asyncio
import json
import re
from datetime import date

import pytest
from fastapi.testclient import TestClient
from mcp.server.mcpserver.exceptions import ToolError

from app import mcp_server
from app.main import app
from app.services import campus_places, student_services


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def call(tool: str, **arguments):
    result = asyncio.run(mcp_server.mcp.call_tool(tool, arguments))
    assert not result.is_error
    return [json.loads(block.text) for block in result.content]


# The worked examples on the OIA insurance page, for a student arriving on
# 10 September. The page prints no year; 2026 keeps February out of the way.
@pytest.mark.parametrize(
    ("trips", "expected", "restarted"),
    [
        ([], date(2027, 3, 10), False),
        ([(date(2026, 10, 1), date(2026, 10, 5))], date(2027, 3, 15), False),
        ([(date(2026, 11, 1), date(2026, 11, 30))], date(2027, 5, 30), True),
        (
            [
                (date(2026, 10, 1), date(2026, 10, 6)),
                (date(2026, 12, 15), date(2026, 12, 25)),
            ],
            date(2027, 6, 25),
            True,
        ),
    ],
    ids=["no trip", "five days", "thirty days", "two trips"],
)
def test_nhi_date_matches_the_examples_on_the_oia_page(trips, expected, restarted):
    answer = student_services.nhi_enrollment(date(2026, 9, 10), trips)

    assert answer["nhi_enrollment_date"] == expected.isoformat()
    assert answer["counting_restarted"] is restarted
    assert answer["source"]["url"].startswith("https://oia.ncu.edu.tw/")


def test_the_examples_are_still_what_the_page_says():
    """If the page's rule changes, the calculation must be revisited."""
    page = (student_services.DATA_DIR / "corpus/oia-insurance.md").read_text("utf-8")

    for phrase in (
        "more than six months",
        "less than 30 days",
        "NHI Enrollment Date: Mar. 10",
        "New NHI Date: Mar. 15",
        "New NHI Date: May 30",
        "New NHI Date: Jun. 25",
    ):
        assert phrase in " ".join(page.split())


def test_private_insurance_ends_the_day_before_nhi_starts():
    answer = student_services.nhi_enrollment(date(2026, 9, 10), [])

    assert answer["private_insurance_until"] == "2027-03-09"


def test_a_29_day_trip_postpones_and_a_30_day_trip_restarts():
    short = student_services.nhi_enrollment(
        date(2026, 9, 10), [(date(2026, 11, 1), date(2026, 11, 29))]
    )
    long = student_services.nhi_enrollment(
        date(2026, 9, 10), [(date(2026, 11, 1), date(2026, 11, 30))]
    )

    assert short["nhi_enrollment_date"] == "2027-04-08"
    assert long["nhi_enrollment_date"] == "2027-05-30"


def test_six_months_from_the_end_of_august_lands_on_the_end_of_february():
    answer = student_services.nhi_enrollment(date(2026, 8, 31), [])

    assert answer["nhi_enrollment_date"] == "2027-02-28"


def test_a_trip_after_enrolment_changes_nothing_and_is_reported():
    answer = student_services.nhi_enrollment(
        date(2026, 9, 10), [(date(2027, 7, 1), date(2027, 8, 20))]
    )

    assert answer["nhi_enrollment_date"] == "2027-03-10"
    assert any("not counted" in text for text in answer["assumptions"])


@pytest.mark.parametrize(
    "trips",
    [
        [(date(2026, 10, 9), date(2026, 10, 1))],
        [(date(2026, 9, 1), date(2026, 9, 5))],
        [
            (date(2026, 10, 1), date(2026, 10, 9)),
            (date(2026, 10, 5), date(2026, 10, 12)),
        ],
    ],
    ids=["returns before leaving", "leaves before arriving", "overlapping"],
)
def test_impossible_trips_are_rejected(trips):
    with pytest.raises(ValueError):
        student_services.nhi_enrollment(date(2026, 9, 10), trips)


def test_every_source_has_a_corpus_file_from_an_official_ncu_page():
    for source in student_services.load_sources():
        assert source["url"].startswith("https://oia.ncu.edu.tw/")
        text = (student_services.DATA_DIR / "corpus" / f"{source['id']}.md").read_text(
            "utf-8"
        )
        assert f'id: "{source["id"]}"' in text


@pytest.mark.parametrize(
    ("query", "expected"),
    [
        ("when can I join NHI health insurance", "oia-insurance"),
        ("work permit hours per week", "oia-work-permit"),
        ("ARC extension form", "oia-immigration"),
        ("寒暑假期間受獎生離開學校超過幾天？", "oia-scholarship-guidelines"),
    ],
)
def test_search_puts_the_right_page_first(query, expected):
    best = student_services.search(query)[0]

    assert best["id"] == expected
    assert best["url"].startswith("https://")
    assert best["fetched_on"]


def test_search_returns_the_passage_that_holds_the_answer():
    best = student_services.search("work permit maximum working hours per week")[0]

    assert "20 hours per week" in best["passage"]


def test_search_keeps_the_links_to_forms():
    best = student_services.search("ARC extension", topic="immigration")[0]

    assert "<https://oia.ncu.edu.tw/static/file/" in best["passage"]


def test_search_returns_nothing_for_what_the_pages_do_not_cover():
    assert student_services.search("pizza") == []
    assert student_services.search("the of and") == []


def test_search_can_return_several_passages_of_one_page_but_none_overlapping():
    found = student_services.search("scholarship stipend PhD", limit=5)
    lines = [row["passage"].split("\n") for row in found if row["id"] == found[0]["id"]]

    assert len(lines) > 1
    for index, passage in enumerate(lines):
        for other in lines[index + 1 :]:
            assert not set(passage) & set(other)


def test_search_matches_latin_words_whole():
    # "arc" is inside "March" and "research", "nhi" inside Vietnamese "nhiêu".
    for row in student_services.search("arc", limit=10):
        assert re.search(r"\bARC\b", row["title"] + "\n" + row["passage"])
    assert student_services.search("bao nhiêu") == []


def test_search_reads_a_plural_as_its_singular():
    best = student_services.search("work permits")[0]

    assert best["id"] == "oia-work-permit"
    assert best["matched_words"] == ["work", "permits"]


def test_search_returns_nothing_when_only_common_words_match():
    # "get" and "student" are on the pages; a driver's licence is not.
    assert student_services.search("how do I get a driver license as a student") == []
    # A single word is still a search, however common the word.
    assert student_services.search("student")


@pytest.mark.parametrize(
    ("query", "expected"),
    [
        ("居留證延期", "oia-immigration"),
        ("gia hạn thẻ cư trú", "oia-immigration"),
        ("perpanjangan izin tinggal", "oia-immigration"),
        ("工作證 每週", "oia-work-permit"),
        ("giấy phép lao động", "oia-work-permit"),
        ("izin kerja", "oia-work-permit"),
    ],
)
def test_search_finds_english_pages_through_the_glossary(query, expected):
    # Not always first: a Chinese question also matches the Chinese guidelines.
    assert expected in [row["id"] for row in student_services.search(query, limit=3)]


def test_glossary_only_adds_words_the_pages_use():
    glossary = json.loads(
        (student_services.DATA_DIR / "glossary.json").read_text("utf-8")
    )
    pages = "\n".join(
        f"{document.title}\n" + "\n".join(document.lines)
        for document in student_services.load_documents()
    ).casefold()

    for entry in glossary["terms"]:
        for word in entry["en"].casefold().split():
            assert word in pages, entry["en"]
        assert any(entry[lang] for lang in student_services.GLOSSARY_LANGS)
    assert student_services.english_terms("What are the working hours?") == []


def test_contacts_carry_no_personal_details_and_point_to_a_known_place():
    contacts = student_services.load_contacts()

    assert "@" not in json.dumps(contacts)
    for office in contacts["offices"]:
        assert campus_places.get(office["place_id"]) is not None
        assert office["phone"].startswith("+886-3-")


def test_contact_for_a_topic():
    (office,) = student_services.find_contacts("work_permit")

    assert office["id"] == "oia-degree-students"
    assert student_services.find_contacts("no-such-topic") == []


def test_search_endpoint(client: TestClient):
    response = client.get(
        "/api/v1/intl/services/search", params={"q": "clinic reimbursement", "limit": 2}
    )

    assert response.status_code == 200
    body = response.json()
    assert "insurance" in body["topics"]
    assert 1 <= len(body["passages"]) <= 2


def test_search_endpoint_rejects_an_unknown_topic(client: TestClient):
    response = client.get(
        "/api/v1/intl/services/search", params={"q": "insurance", "topic": "nope"}
    )

    assert response.status_code == 422


def test_contacts_endpoint(client: TestClient):
    response = client.get("/api/v1/intl/contacts", params={"topic": "exchange"})

    assert response.status_code == 200
    body = response.json()
    assert [office["id"] for office in body["offices"]] == ["oia-exchange-students"]
    assert body["switchboard"].startswith("+886")


def test_nhi_endpoint(client: TestClient):
    response = client.post(
        "/api/v1/intl/nhi-enrollment-date",
        json={
            "arrival_date": "2026-09-10",
            "trips_abroad": [{"departure": "2026-10-01", "reentry": "2026-10-05"}],
        },
    )

    assert response.status_code == 200
    assert response.json()["nhi_enrollment_date"] == "2027-03-15"


def test_nhi_endpoint_rejects_impossible_trips(client: TestClient):
    response = client.post(
        "/api/v1/intl/nhi-enrollment-date",
        json={
            "arrival_date": "2026-09-10",
            "trips_abroad": [{"departure": "2026-10-09", "reentry": "2026-10-01"}],
        },
    )

    assert response.status_code == 422


def test_mcp_nhi_tool_computes_the_date():
    (answer,) = call(
        "estimate_nhi_enrollment_date",
        arrival_date="2026-09-10",
        trips_abroad=[["2026-11-01", "2026-11-30"]],
    )

    assert answer["nhi_enrollment_date"] == "2027-05-30"


def test_mcp_nhi_tool_explains_a_bad_date():
    with pytest.raises(ToolError, match="YYYY-MM-DD"):
        asyncio.run(
            mcp_server.mcp.call_tool(
                "estimate_nhi_enrollment_date", {"arrival_date": "10 September"}
            )
        )


def test_mcp_search_and_contact_tools():
    (found,) = call("search_student_services", query="work permit documents")
    (contact,) = call("get_student_service_contact", topic="insurance")

    assert found["passages"][0]["id"] == "oia-work-permit"
    (office,) = contact["offices"]
    assert office["place"]["code"] == "TR"
    assert "google_walking_directions" in office["place"]["map_links"]
