import asyncio
import json

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.mcp_server import mcp
from app.services import campus_places

# Generous box around the campus and the streets next to it.
LATITUDE = (24.955, 24.980)
LONGITUDE = (121.180, 121.205)


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def ids(response) -> list[str]:
    return [place["id"] for place in response.json()["places"]]


def test_every_place_is_near_campus_and_has_both_names():
    places = campus_places.load().places
    assert places
    for place in places:
        assert LATITUDE[0] < place.latitude < LATITUDE[1], place.id
        assert LONGITUDE[0] < place.longitude < LONGITUDE[1], place.id
        assert place.name_zh.strip() and place.name_en.strip(), place.id


def test_building_codes_are_unique():
    codes = [place.code for place in campus_places.load().places if place.code]
    assert codes
    assert len(codes) == len(set(codes))


def test_lists_every_place_with_attribution(client: TestClient):
    response = client.get("/api/v1/campus/places")
    assert response.status_code == 200
    body = response.json()
    assert "OpenStreetMap" in body["attribution"]
    assert len(body["places"]) == len(campus_places.load().places)


@pytest.mark.parametrize(
    ("query", "expected"),
    [
        ("工程五館", "engineering-building-5"),
        ("e6", "engineering-building-5"),
        ("engineering building 5", "engineering-building-5"),
        ("OIA", "teaching-research-building"),
        ("教學研究綜合大樓", "teaching-research-building"),
        ("i1", "management-building-2"),
        ("大講堂", "history-museum-lecture-hall"),
        ("  國際事務處 ", "teaching-research-building"),
    ],
)
def test_search_matches_names_codes_and_aliases(client: TestClient, query, expected):
    assert expected in ids(client.get("/api/v1/campus/places", params={"q": query}))


def test_search_without_a_match_is_empty(client: TestClient):
    assert ids(client.get("/api/v1/campus/places", params={"q": "不存在的地方"})) == []


def test_category_filter_combines_with_search(client: TestClient):
    transit = client.get("/api/v1/campus/places", params={"category": "transit"})
    assert transit.json()["places"]
    assert {place["category"] for place in transit.json()["places"]} == {"transit"}

    # 依仁堂 is both a gymnasium and a bus stop.
    both = client.get("/api/v1/campus/places", params={"q": "依仁堂"})
    assert set(ids(both)) == {"yi-ren-gymnasium", "bus-stop-yi-ren-hall"}
    only_stop = client.get(
        "/api/v1/campus/places", params={"q": "依仁堂", "category": "transit"}
    )
    assert ids(only_stop) == ["bus-stop-yi-ren-hall"]


def test_unknown_category_is_rejected(client: TestClient):
    response = client.get("/api/v1/campus/places", params={"category": "nowhere"})
    assert response.status_code == 422


def test_place_carries_map_links_to_its_coordinates(client: TestClient):
    response = client.get("/api/v1/campus/places/engineering-building-5")
    assert response.status_code == 200
    place = response.json()
    assert place["code"] == "E6"
    assert place["map_links"] == {
        "google_maps": "https://www.google.com/maps/search/?api=1&query=24.967014%2C121.187598",
        "google_walking_directions": "https://www.google.com/maps/dir/?api=1&destination=24.967014%2C121.187598&travelmode=walking",
        "apple_maps": "https://maps.apple.com/?daddr=24.967014%2C121.187598&dirflg=w",
    }


def test_unknown_place_is_not_found(client: TestClient):
    assert client.get("/api/v1/campus/places/nowhere").status_code == 404


def test_mcp_tool_finds_a_place_with_directions():
    call = asyncio.run(mcp.call_tool("find_place", {"query": "OIA"}))
    result = json.loads(call.content[0].text)
    assert "OpenStreetMap" in result["attribution"]
    [place] = result["places"]
    assert place["id"] == "teaching-research-building"
    assert place["map_links"]["google_walking_directions"].startswith(
        "https://www.google.com/maps/dir/"
    )
