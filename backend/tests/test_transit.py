import json
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.services import places, tdx
from app.services.tdx import TdxClient, parse_arrivals, parse_stop_positions

# Route 132 as TDX returned it at 04:52, before the first bus of the day.
BEFORE_SERVICE = json.loads(
    (Path(__file__).parent / "fixtures" / "tdx_eta_132_before_service.json").read_text(
        encoding="utf-8"
    )
)


def running_bus_row() -> dict:
    # Hand-written from TDX's documented schema, not a recorded response.
    return {
        **BEFORE_SERVICE[3],
        "PlateNumb": "KKA-0001",
        "StopStatus": 0,
        "EstimateTime": 240,
    }


def stop_of_route_rows() -> list[dict]:
    # Hand-written from TDX's documented schema, not a recorded response; the
    # coordinates are placeholders near the real stops.
    def stop(uid: str, lat: float | None, lon: float | None) -> dict:
        return {
            "StopUID": uid,
            "StopPosition": {"PositionLat": lat, "PositionLon": lon},
        }

    return [
        {
            "RouteName": {"Zh_tw": "132", "En": "132"},
            "Direction": 0,
            "Stops": [
                stop("TAO269", 24.9655, 121.1945),
                stop("TAO270", 24.9668, 121.1940),
                stop("TAO2548", 0, 0),
                stop("TAO2549", 2760000.0, 270000.0),
                stop("TAO289", None, None),
            ],
        }
    ]


class FakeTdx:
    """Stands in for TDX and counts what it was asked."""

    def __init__(self, rows: list[dict]):
        self.rows = rows
        self.token_requests = 0
        self.data_requests: list[httpx.Request] = []
        self.reject_next_data_request = False
        self.stop_requests = 0
        self.stops_down = False

    def __call__(self, request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/token"):
            self.token_requests += 1
            return httpx.Response(
                200,
                json={"access_token": f"t{self.token_requests}", "expires_in": 86400},
            )
        if "/StopOfRoute/" in request.url.path:
            self.stop_requests += 1
            if self.stops_down:
                return httpx.Response(500)
            return httpx.Response(200, json=stop_of_route_rows())
        self.data_requests.append(request)
        if self.reject_next_data_request:
            self.reject_next_data_request = False
            return httpx.Response(401)
        return httpx.Response(200, json=self.rows)


@pytest.fixture(name="fake")
def fake_fixture(monkeypatch: pytest.MonkeyPatch):
    fake = FakeTdx(BEFORE_SERVICE)
    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(
        tdx, "tdx_client", TdxClient(transport=httpx.MockTransport(fake))
    )
    return fake


@pytest.fixture(name="client")
def client_fixture():
    return TestClient(app)


def test_arrivals_come_back_in_route_order():
    arrivals = parse_arrivals(BEFORE_SERVICE)
    assert [a.sequence for a in arrivals] == list(range(1, 35))
    assert arrivals[0].stop_name.zh == "中壢公車站"
    assert arrivals[-1].stop_name.zh == "中壢公車站"


def test_before_service_reports_the_next_scheduled_bus():
    main_gate = next(a for a in parse_arrivals(BEFORE_SERVICE) if a.sequence == 14)
    assert main_gate.status == "NOT_DEPARTED"
    assert main_gate.estimate_seconds is None
    assert main_gate.plate_number is None
    assert main_gate.next_bus_time.isoformat() == "2026-11-10T07:18:00+08:00"


def test_running_bus_reports_seconds_and_plate():
    (arrival,) = parse_arrivals([running_bus_row()])
    assert arrival.status == "ARRIVING"
    assert arrival.estimate_seconds == 240
    assert arrival.plate_number == "KKA-0001"


def test_campus_stops_are_flagged_and_renamed():
    campus = [a for a in parse_arrivals(BEFORE_SERVICE) if a.on_campus]
    assert [a.sequence for a in campus] == [14, 15, 16, 17, 18, 19, 20]
    # The loop passes the main gate twice, under two stop ids.
    assert [a.stop_uid for a in campus if a.stop_name.en == "NCU Main Gate"] == [
        "TAO269",
        "TAO275",
    ]
    off_campus = next(a for a in parse_arrivals(BEFORE_SERVICE) if a.sequence == 25)
    assert off_campus.stop_name.en == "Youmin Hospital"


def test_endpoint_returns_campus_stops(client: TestClient, fake: FakeTdx):
    resp = client.get("/api/v1/transit/bus/132/arrivals", params={"campus_only": True})
    assert resp.status_code == 200
    body = resp.json()
    assert body["route"] == "132"
    assert len(body["stops"]) == 7
    assert body["stops"][0]["stop_name"] == {
        "zh": "中央大學正門",
        "en": "NCU Main Gate",
    }
    request = fake.data_requests[0]
    assert request.url.path.endswith("/EstimatedTimeOfArrival/City/Taoyuan/132")
    assert request.headers["Authorization"] == "Bearer t1"


def test_intercity_route_uses_the_intercity_path(client: TestClient, fake: FakeTdx):
    fake.rows = []
    assert client.get("/api/v1/transit/bus/9025A/arrivals").status_code == 200
    assert fake.data_requests[0].url.path.endswith(
        "/EstimatedTimeOfArrival/InterCity/9025A"
    )


def test_repeat_requests_reuse_the_token_and_the_arrivals(
    client: TestClient, fake: FakeTdx
):
    for _ in range(3):
        assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 200
    assert fake.token_requests == 1
    assert len(fake.data_requests) == 1


def test_rejected_token_is_renewed_once(client: TestClient, fake: FakeTdx):
    fake.reject_next_data_request = True
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 200
    assert fake.token_requests == 2
    assert fake.data_requests[-1].headers["Authorization"] == "Bearer t2"


def test_other_routes_sharing_the_prefix_are_dropped(client: TestClient, fake: FakeTdx):
    other = {**BEFORE_SERVICE[0], "RouteName": {"Zh_tw": "132A", "En": "132A"}}
    fake.rows = [BEFORE_SERVICE[0], other]
    body = client.get("/api/v1/transit/bus/132/arrivals").json()
    assert len(body["stops"]) == 1


def test_unknown_route_is_404(client: TestClient, fake: FakeTdx):
    assert client.get("/api/v1/transit/bus/999/arrivals").status_code == 404
    assert fake.data_requests == []


def test_missing_credentials_is_503(
    client: TestClient, fake: FakeTdx, monkeypatch: pytest.MonkeyPatch
):
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", None)
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 503


def test_tdx_failure_is_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def down(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("down")

    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(
        tdx, "tdx_client", TdxClient(transport=httpx.MockTransport(down))
    )
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


def test_only_plausible_coordinates_are_kept():
    positions = parse_stop_positions(stop_of_route_rows())
    assert set(positions) == {"TAO269", "TAO270"}
    assert (positions["TAO269"].lat, positions["TAO269"].lon) == (24.9655, 121.1945)


def test_arrivals_carry_stop_coordinates(client: TestClient, fake: FakeTdx):
    for _ in range(2):
        body = client.get("/api/v1/transit/bus/132/arrivals").json()
    by_uid = {s["stop_uid"]: s for s in body["stops"]}
    assert by_uid["TAO269"]["position"] == {"lat": 24.9655, "lon": 121.1945}
    assert by_uid["TAO2548"]["position"] is None
    assert fake.stop_requests == 1


def test_arrivals_survive_a_failing_stop_list(client: TestClient, fake: FakeTdx):
    fake.stops_down = True
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert resp.status_code == 200
    assert all(s["position"] is None for s in resp.json()["stops"])


def test_places_file_is_valid_and_ids_are_unique():
    loaded = places.load_places()
    assert len({p.id for p in loaded}) == len(loaded) > 0
    for place in loaded:
        assert place.sources, place.id
        assert set(place.bus_routes) <= set(tdx.ROUTE_SCOPES), place.id


def test_place_links_to_transit_directions_from_ncu(client: TestClient):
    body = client.get("/api/v1/transit/places/nia-taoyuan-service-center").json()
    assert body["address"]["zh"] == "桃園市桃園區縣府路106號1樓"
    assert body["maps_url"] == (
        "https://www.google.com/maps/dir/?api=1&origin=24.9684%2C121.1916"
        "&destination=24.9943647%2C121.3027015&travelmode=transit"
    )


def test_place_without_coordinates_links_by_address(client: TestClient):
    body = client.get("/api/v1/transit/places/nhi-northern-division").json()
    assert body["position"] is None
    assert "destination=%E6%A1%83%E5%9C%92%E5%B8%82" in body["maps_url"]


def test_places_filter_by_category(client: TestClient):
    body = client.get("/api/v1/transit/places", params={"category": "station"}).json()
    assert [p["id"] for p in body] == ["zhongli-train-station", "thsr-taoyuan-station"]
    assert client.get("/api/v1/transit/places/nowhere").status_code == 404
    assert (
        client.get("/api/v1/transit/places", params={"category": "x"}).status_code
        == 422
    )
