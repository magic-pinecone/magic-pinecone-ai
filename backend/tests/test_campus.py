import asyncio
import json

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

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
        ("R3", "research-center-building-phase-2"),
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

    # 依仁堂 is a gymnasium, a bus stop and a YouBike station.
    both = client.get("/api/v1/campus/places", params={"q": "依仁堂"})
    assert set(ids(both)) == {
        "yi-ren-gymnasium",
        "bus-stop-yi-ren-hall",
        "yiren-youbike",
    }
    only_gym = client.get(
        "/api/v1/campus/places", params={"q": "依仁堂", "category": "sports"}
    )
    assert ids(only_gym) == ["yi-ren-gymnasium"]


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


@pytest.mark.parametrize(
    ("query", "expected_code"), [("A", "A"), ("E", "E"), ("e6", "E6"), ("S", "S")]
)
def test_an_exact_building_code_comes_first(client: TestClient, query, expected_code):
    places = client.get("/api/v1/campus/places", params={"q": query}).json()["places"]

    assert places[0]["code"] == expected_code


FULL_PLACE = {
    "id": "noodle-shop",
    "name_zh": "麵店",
    "name_en": "Noodle Shop",
    "name_vi": "Quán mì",
    "code": None,
    "category": "dining",
    "latitude": 24.967,
    "longitude": 121.19,
    "osm_id": None,
    "aliases": [],
    "description": {"zh": "牛肉麵", "en": "Beef noodles", "vi": "Mì bò"},
    "opening_hours": "Mo-Sa 11:00-20:00",
    "price_range_twd": {"min": 80, "max": 150},
    "price_note": {"zh": "牛肉麵 120", "en": "Beef noodles 120", "vi": "Mì bò 120"},
    "tags": ["halal", "english_menu"],
    "sources": ["https://example.com/noodle-shop"],
}


@pytest.fixture(name="custom_places")
def custom_places_fixture(tmp_path, monkeypatch):
    def use(*places):
        data_file = tmp_path / "places.json"
        data_file.write_text(
            json.dumps(
                {"source": "test", "attribution": "test", "places": list(places)}
            ),
            encoding="utf-8",
        )
        monkeypatch.setattr(campus_places, "DATA_FILE", data_file)
        campus_places.load.cache_clear()

    yield use
    campus_places.load.cache_clear()


def test_a_place_with_every_new_field_round_trips(client: TestClient, custom_places):
    custom_places(FULL_PLACE)

    [place] = client.get("/api/v1/campus/places").json()["places"]

    assert {key: place[key] for key in FULL_PLACE} == FULL_PLACE
    assert place["map_links"]["apple_maps"].startswith("https://maps.apple.com/")


def test_new_fields_default_when_a_place_omits_them(client: TestClient, custom_places):
    bare = {
        key: FULL_PLACE[key]
        for key in (
            "id", "name_zh", "name_en", "code", "category",
            "latitude", "longitude", "osm_id", "aliases",
        )
    }  # fmt: skip
    custom_places({**bare, "category": "shop"})

    [place] = client.get("/api/v1/campus/places", params={"category": "shop"}).json()[
        "places"
    ]

    assert place["name_vi"] is None
    assert place["description"] is None
    assert place["opening_hours"] is None
    assert place["price_range_twd"] is None
    assert place["price_note"] is None
    assert place["tags"] == []
    assert place["sources"] == []


def test_search_matches_a_vietnamese_name(client: TestClient, custom_places):
    custom_places(FULL_PLACE)

    found = client.get("/api/v1/campus/places", params={"q": "quán mì"})

    assert ids(found) == ["noodle-shop"]


@pytest.mark.parametrize(
    "price_range",
    [{"min": 200, "max": 100}, {"min": -1, "max": 100}, {"min": 0, "max": -5}],
)
def test_an_invalid_price_range_is_rejected_at_load(custom_places, price_range):
    custom_places({**FULL_PLACE, "price_range_twd": price_range})

    with pytest.raises(ValidationError):
        campus_places.load()


def test_an_unknown_tag_is_rejected_at_load(custom_places):
    custom_places({**FULL_PLACE, "tags": ["gluten_free"]})

    with pytest.raises(ValidationError):
        campus_places.load()


def test_a_shop_inside_a_building_names_its_host(client: TestClient, custom_places):
    hall = {**FULL_PLACE, "id": "dining-hall", "osm_id": "way/1"}
    custom_places(hall, {**FULL_PLACE, "located_in": "dining-hall"})

    place = client.get("/api/v1/campus/places/noodle-shop").json()

    assert place["located_in"] == "dining-hall"


@pytest.mark.parametrize("host", ["nowhere", "kiosk"])
def test_located_in_must_name_a_top_level_place(custom_places, host):
    nested = {**FULL_PLACE, "id": "kiosk", "located_in": "noodle-shop"}
    custom_places({**FULL_PLACE, "located_in": host}, nested)

    with pytest.raises(ValueError, match="not a top-level place"):
        campus_places.load()


def test_campus_map_is_geojson_linked_to_places(client: TestClient):
    response = client.get("/api/v1/campus/map.geojson")

    assert response.headers["content-type"] == "application/geo+json"
    collection = response.json()
    assert collection["type"] == "FeatureCollection"
    assert "OpenStreetMap" in collection["attribution"]
    kinds = {feature["properties"]["kind"] for feature in collection["features"]}
    assert {"campus", "building"} <= kinds
    place_ids = {place.id for place in campus_places.load().places}
    linked = {
        feature["properties"]["place_id"]
        for feature in collection["features"]
        if feature["properties"]["place_id"]
    }
    assert linked and linked <= place_ids


CSIE = {
    "name_zh": "資訊工程學系",
    "name_en": "Department of Computer Science and Information Engineering",
    "name_vi": None,
    "kind": "department",
    "aliases": ["資工系", "CSIE"],
    "floor": "3F",
    "website": "https://www.csie.ncu.edu.tw/",
    "sources": ["https://example.com/csie-contact"],
}


def test_a_building_lists_the_units_inside(client: TestClient, custom_places):
    custom_places({**FULL_PLACE, "units": [CSIE]})

    [place] = client.get("/api/v1/campus/places").json()["places"]

    assert place["units"] == [CSIE]


def test_units_default_to_empty(client: TestClient, custom_places):
    custom_places(FULL_PLACE)

    [place] = client.get("/api/v1/campus/places").json()["places"]

    assert place["units"] == []


@pytest.mark.parametrize("query", ["資工系", "csie", "資訊工程"])
def test_search_finds_the_building_of_a_unit(client: TestClient, custom_places, query):
    custom_places(FULL_PLACE, {**FULL_PLACE, "id": "e5", "units": [CSIE]})

    assert ids(client.get("/api/v1/campus/places", params={"q": query})) == ["e5"]


def test_a_building_named_by_the_query_outranks_one_housing_a_unit(
    client: TestClient, custom_places
):
    housing = {**FULL_PLACE, "id": "e5", "units": [{**CSIE, "aliases": ["麵"]}]}
    custom_places(housing, {**FULL_PLACE, "name_zh": "麵"})

    assert ids(client.get("/api/v1/campus/places", params={"q": "麵"})) == [
        "noodle-shop",
        "e5",
    ]


@pytest.mark.parametrize(
    "query, expected",
    [
        ("資工系", "engineering-building-5"),
        ("輔導處", "second-administration-center"),
        ("諮商中心", "second-administration-center"),
        ("教學發展中心", "teaching-research-building"),
        ("註冊組", "teaching-research-building"),
        ("學務處", "second-administration-center"),
    ],
)
def test_the_curated_units_lead_to_their_building(client: TestClient, query, expected):
    assert ids(client.get("/api/v1/campus/places", params={"q": query}))[0] == expected


def test_every_curated_unit_cites_a_web_source():
    units = [unit for place in campus_places.load().places for unit in place.units]

    assert len(units) > 50
    assert all(
        source.startswith("https://") for unit in units for source in unit.sources
    )


@pytest.mark.parametrize("unit", [{**CSIE, "sources": []}, {**CSIE, "kind": "lab"}])
def test_an_unsourced_or_unknown_unit_is_rejected_at_load(custom_places, unit):
    custom_places({**FULL_PLACE, "units": [unit]})

    with pytest.raises(ValidationError):
        campus_places.load()
