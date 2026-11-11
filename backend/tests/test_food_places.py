"""Food places around campus: halal-certified and nearby restaurants."""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_lists_food_places_with_both_sources_attributed():
    body = client.get("/api/v1/campus/food").json()

    assert "OpenStreetMap" in body["attribution"]
    assert "Tourism Administration" in body["attribution"]
    assert any(place["halal"] for place in body["places"])
    assert any(place["halal"] is None for place in body["places"])


def test_halal_only_keeps_certified_places_with_their_certifier():
    places = client.get("/api/v1/campus/food", params={"halal_only": True}).json()[
        "places"
    ]

    assert places
    for place in places:
        assert place["halal"]["certificates"], place["id"]
        assert place["halal"]["certifier"], place["id"]


def test_food_places_skip_what_campus_places_already_lists():
    campus = client.get("/api/v1/campus/places").json()["places"]
    food = client.get("/api/v1/campus/food").json()["places"]
    campus_osm = {place["osm_id"] for place in campus if place["osm_id"]}

    assert not campus_osm & {place["osm_id"] for place in food if place["osm_id"]}


def test_food_place_links_to_walking_directions():
    place = client.get("/api/v1/campus/food").json()["places"][0]

    assert str(place["latitude"]) in place["map_links"]["google_walking_directions"]
