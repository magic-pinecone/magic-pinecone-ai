import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.services import destinations, tdx
from app.services.tdx import TdxClient, parse_arrivals, parse_stop_positions

FIXTURES = Path(__file__).parent / "fixtures"


def fixture(name: str) -> list[dict]:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


# Recorded TDX responses, trimmed to a few rows where noted.
# Route 132 at 04:52, before the first bus of the day (every row).
BEFORE_SERVICE = fixture("tdx_eta_132_before_service.json")
# InterCity route 9025 at 23:02: both directions of sub-route 9025A around the
# campus, and the first two stops of the main 9025 branch.
ETA_9025 = fixture("tdx_eta_9025_after_last_bus.json")
# Route 173 at 23:02: the campus end of both directions.
ETA_173 = fixture("tdx_eta_173_after_last_bus.json")
# StopOfRoute for 9025, each branch cut down to its first and campus stops.
STOPS_9025 = fixture("tdx_stops_9025.json")


def eta_rows(route: str, stops: list[tuple[str, str, str, int, int]]) -> list[dict]:
    """EstimatedTimeOfArrival rows in TDX's shape for (uid, zh, en, direction, seq)."""
    return [
        {
            **BEFORE_SERVICE[0],
            "StopUID": uid,
            "StopName": {"Zh_tw": zh, "En": en},
            "RouteName": {"Zh_tw": route, "En": route},
            "SubRouteName": {"Zh_tw": route, "En": route},
            "Direction": direction,
            "StopSequence": sequence,
        }
        for uid, zh, en, direction, sequence in stops
    ]


# The stop ids and Chinese names TDX returned for 133 and 172 on 2026-11-10, put
# back into TDX's row shape by hand: the raw responses were not kept, and some
# of the English names here stand in for ones the old code had already replaced.
ETA_133 = eta_rows(
    "133",
    [
        ("TAO5642", "中壢客運中壢總站", "Zhongli Bus Main Station", 0, 1),
        ("TAO269", "中央大學正門", "The Front Door of Central University", 0, 14),
        ("TAO270", "中央大學警衛室", "Guardroom of Central University", 0, 15),
        ("TAO2620", "中大湖", "Zhongda Lake", 0, 16),
        ("TAO272", "中央大學依仁堂", "Yi-Ren-Tang of Central University", 0, 17),
        ("TAO273", "中央大學後門", "The Back Door of Central University", 0, 18),
        ("TAO274", "中央大學觀景台", "Observatory of Central University", 0, 19),
        ("TAO275", "中央大學正門", "The Front Door of Central University", 0, 20),
    ],
)
ETA_172 = eta_rows(
    "172",
    [
        ("TAO5773", "中央大學警衛室", "National Central University Guard Room", 0, 1),
        ("TAO2619", "中大湖", "Zhongda Lake", 0, 2),
        ("TAO5775", "中央大學依仁堂", "Yi-Ren-Tang of Central University", 0, 3),
        ("TAO5776", "中央大學後門", "The Back Door of Central University", 0, 4),
        ("TAO5777", "中央大學觀景台", "Observatory of Central University", 0, 5),
        ("TAO275", "中央大學正門", "The Front Door of Central University", 0, 6),
        ("TAO64235", "潤泰公司", "Runtai Company", 0, 7),
        ("TAO269", "中央大學正門", "The Front Door of Central University", 1, 16),
        ("TAO5773", "中央大學警衛室", "National Central University Guard Room", 1, 17),
        ("TAO2619", "中大湖", "Zhongda Lake", 1, 18),
        ("TAO5800", "中央大學依仁堂", "Yi-Ren-Tang of Central University", 1, 19),
        ("TAO5799", "中央大學後門", "The Back Door of Central University", 1, 20),
        ("TAO5798", "中央大學觀景台", "National Central University Observatory", 1, 21),
    ],
)


def running_bus_row(estimate: int = 240) -> dict:
    # Hand-written from TDX's documented schema, not a recorded response.
    return {
        **BEFORE_SERVICE[3],
        "PlateNumb": "KKA-0001",
        "StopStatus": 0,
        "EstimateTime": estimate,
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


class FakeClock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


class FakeTdx:
    """Stands in for TDX and counts what it was asked."""

    def __init__(self, rows: list[dict]):
        self.rows = rows
        self.stop_rows = stop_of_route_rows()
        self.token_requests = 0
        self.data_requests: list[httpx.Request] = []
        self.reject_next_data_request = False
        self.stop_requests = 0
        # When set, what the matching endpoint answers instead of its data.
        self.token_response: httpx.Response | None = None
        self.data_response: httpx.Response | None = None
        self.stops_response: httpx.Response | None = None

    def __call__(self, request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/token"):
            self.token_requests += 1
            return self.token_response or httpx.Response(
                200,
                json={"access_token": f"t{self.token_requests}", "expires_in": 86400},
            )
        if "/StopOfRoute/" in request.url.path:
            self.stop_requests += 1
            return self.stops_response or httpx.Response(200, json=self.stop_rows)
        self.data_requests.append(request)
        if self.reject_next_data_request:
            self.reject_next_data_request = False
            return httpx.Response(401)
        return self.data_response or httpx.Response(200, json=self.rows)


@pytest.fixture(name="clock")
def clock_fixture():
    return FakeClock()


@pytest.fixture(name="fake")
def fake_fixture(monkeypatch: pytest.MonkeyPatch, clock: FakeClock):
    fake = FakeTdx(BEFORE_SERVICE)
    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(settings, "TDX_ARRIVAL_CACHE_SECONDS", 30)
    monkeypatch.setattr(settings, "TDX_STOP_CACHE_HOURS", 24)
    monkeypatch.setattr(
        tdx, "tdx_client", TdxClient(transport=httpx.MockTransport(fake), clock=clock)
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
    assert arrival.status == "EN_ROUTE"
    assert arrival.estimate_seconds == 240
    assert arrival.plate_number == "KKA-0001"


def test_a_bus_far_away_is_en_route_not_arriving():
    # StopStatus 0 only means normal service; this bus is 19 minutes out.
    (arrival,) = parse_arrivals([running_bus_row(estimate=1140)])
    assert arrival.status == "EN_ROUTE"
    assert arrival.estimate_seconds == 1140


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


@pytest.mark.parametrize(
    ("route", "rows", "campus_uids"),
    [
        (
            "133",
            ETA_133,
            ["TAO269", "TAO270", "TAO2620", "TAO272", "TAO273", "TAO274", "TAO275"],
        ),
        (
            "172",
            ETA_172,
            ["TAO5773", "TAO2619", "TAO5775", "TAO5776", "TAO5777", "TAO275"]
            + ["TAO269", "TAO5773", "TAO2619", "TAO5800", "TAO5799", "TAO5798"],
        ),
        (
            "173",
            ETA_173,
            ["TAO8362", "TAO2619", "TAO272", "TAO273", "TAO274", "TAO275"]
            + ["TAO5934", "TAO5935", "TAO2620", "TAO5937", "TAO5938", "TAO274"],
        ),
        ("9025A", ETA_9025, ["THB295243", "THB295246", "THB295243", "THB295246"]),
    ],
)
def test_campus_stops_are_recognised_by_name_on_every_route(
    client: TestClient, fake: FakeTdx, route: str, rows: list[dict], campus_uids: list
):
    fake.rows = rows
    everything = client.get(f"/api/v1/transit/bus/{route}/arrivals").json()["stops"]
    campus = [s for s in everything if s["on_campus"]]
    assert [s["stop_uid"] for s in campus] == campus_uids
    assert 0 < len(campus) < len(everything)
    for stop in campus:
        assert (
            "中央大學" in stop["stop_name"]["zh"] or stop["stop_name"]["zh"] == "中大湖"
        )
        # The campus stops get one English name whatever TDX calls them.
        assert stop["stop_name"]["en"].startswith("NCU ")
    for stop in everything:
        if not stop["on_campus"]:
            assert "中央大學" not in stop["stop_name"]["zh"]
    only = client.get(
        f"/api/v1/transit/bus/{route}/arrivals", params={"campus_only": True}
    ).json()["stops"]
    assert only == campus


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


def test_9025a_is_read_from_intercity_route_9025_by_sub_route(
    client: TestClient, fake: FakeTdx
):
    fake.rows = ETA_9025
    fake.stop_rows = STOPS_9025
    sub_routes = {row["SubRouteName"]["Zh_tw"] for row in ETA_9025}
    assert sub_routes == {"9025", "9025A"}

    resp = client.get("/api/v1/transit/bus/9025A/arrivals")

    assert resp.status_code == 200
    assert fake.data_requests[0].url.path.endswith(
        "/EstimatedTimeOfArrival/InterCity/9025"
    )
    stops = resp.json()["stops"]
    wanted = [row for row in ETA_9025 if row["SubRouteName"]["Zh_tw"] == "9025A"]
    assert len(stops) == len(wanted) == 8
    assert [(s["direction"], s["sequence"]) for s in stops] == [
        (0, 3),
        (0, 4),
        (0, 5),
        (0, 6),
        (1, 5),
        (1, 6),
        (1, 7),
        (1, 8),
    ]
    # 舊社 is only in the fixture as a stop of the main 9025 branch.
    assert "舊社" not in {s["stop_name"]["zh"] for s in stops}
    guard = next(s for s in stops if s["stop_uid"] == "THB295243")
    assert guard["status"] == "LAST_BUS_GONE"
    assert guard["position"] is not None


def test_repeat_requests_reuse_the_token_and_the_arrivals(
    client: TestClient, fake: FakeTdx, clock: FakeClock
):
    for _ in range(3):
        assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 200
    assert fake.token_requests == 1
    assert len(fake.data_requests) == 1
    clock.now += 31
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 200
    assert fake.token_requests == 1
    assert len(fake.data_requests) == 2


def test_simultaneous_requests_cost_one_token_and_one_call(fake: FakeTdx):
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(
            pool.map(lambda _: tdx.tdx_client.bus_arrivals("132"), range(16))
        )
    assert all(len(arrivals) == 34 for arrivals in results)
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


def test_routes_are_listed(client: TestClient):
    assert client.get("/api/v1/transit/bus/routes").json() == [
        "132",
        "133",
        "172",
        "173",
        "9025A",
    ]


def test_unknown_route_is_404(client: TestClient, fake: FakeTdx):
    assert client.get("/api/v1/transit/bus/999/arrivals").status_code == 404
    # 9025 is the TDX name behind 9025A, not a route of its own here.
    assert client.get("/api/v1/transit/bus/9025/arrivals").status_code == 404
    assert fake.data_requests == []


def test_missing_credentials_is_503(
    client: TestClient, fake: FakeTdx, monkeypatch: pytest.MonkeyPatch
):
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", None)
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert resp.status_code == 503
    assert "not configured" in resp.json()["detail"]
    assert "Retry-After" not in resp.headers
    assert fake.token_requests == 0


def test_tdx_failure_is_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def down(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("down")

    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(
        tdx, "tdx_client", TdxClient(transport=httpx.MockTransport(down))
    )
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


def test_tdx_server_error_is_502(client: TestClient, fake: FakeTdx):
    fake.data_response = httpx.Response(500)
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


def test_rate_limited_arrivals_are_503_with_retry_after(
    client: TestClient, fake: FakeTdx
):
    fake.data_response = httpx.Response(429, headers={"Retry-After": "6"})
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert resp.status_code == 503
    assert resp.headers["Retry-After"] == "6"
    assert "rate limiting" in resp.json()["detail"]

    # Nothing was cached: once TDX lets up, the next request gets through.
    fake.data_response = None
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 200


def test_rate_limit_without_a_usable_retry_after_says_a_minute(
    client: TestClient, fake: FakeTdx
):
    fake.data_response = httpx.Response(429, headers={"Retry-After": "soon"})
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert resp.status_code == 503
    assert resp.headers["Retry-After"] == "60"


def test_rate_limited_token_is_503_with_retry_after(client: TestClient, fake: FakeTdx):
    fake.token_response = httpx.Response(429)
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert resp.status_code == 503
    assert resp.headers["Retry-After"] == "60"
    assert fake.data_requests == []


def test_a_body_that_is_not_json_is_502(client: TestClient, fake: FakeTdx):
    fake.data_response = httpx.Response(200, text="<html>Maintenance</html>")
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


def test_json_that_is_not_a_list_of_rows_is_502(client: TestClient, fake: FakeTdx):
    fake.data_response = httpx.Response(200, json={"message": "API not found"})
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


@pytest.mark.parametrize(
    "token_response",
    [
        httpx.Response(200, json={"expires_in": 86400}),
        httpx.Response(200, json={"access_token": "t", "expires_in": "tomorrow"}),
        httpx.Response(200, json=["not", "an", "object"]),
        httpx.Response(200, text="not json"),
        httpx.Response(401, json={"error": "invalid_client"}),
    ],
)
def test_an_unusable_token_response_is_502(
    client: TestClient, fake: FakeTdx, token_response: httpx.Response
):
    fake.token_response = token_response
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502
    assert fake.data_requests == []


def test_a_malformed_next_bus_time_is_502(client: TestClient, fake: FakeTdx):
    fake.rows = [{**BEFORE_SERVICE[0], "NextBusTime": "25:61"}]
    assert client.get("/api/v1/transit/bus/132/arrivals").status_code == 502


def test_only_plausible_coordinates_are_kept():
    positions = parse_stop_positions(stop_of_route_rows())
    assert set(positions) == {"TAO269", "TAO270"}
    assert (positions["TAO269"].lat, positions["TAO269"].lon) == (24.9655, 121.1945)


def test_arrivals_carry_stop_coordinates(
    client: TestClient, fake: FakeTdx, clock: FakeClock
):
    for _ in range(2):
        body = client.get("/api/v1/transit/bus/132/arrivals").json()
        # Past the arrivals cache and far past the one for failures.
        clock.now += 3600
    by_uid = {s["stop_uid"]: s for s in body["stops"]}
    assert by_uid["TAO269"]["position"] == {"lat": 24.9655, "lon": 121.1945}
    assert by_uid["TAO2548"]["position"] is None
    assert len(fake.data_requests) == 2
    assert fake.stop_requests == 1

    clock.now += 24 * 3600
    client.get("/api/v1/transit/bus/132/arrivals")
    assert fake.stop_requests == 2


@pytest.mark.parametrize(
    "stops_response",
    [
        httpx.Response(500),
        httpx.Response(429, headers={"Retry-After": "6"}),
        httpx.Response(200, text="not json"),
        httpx.Response(200, json=[]),
    ],
)
def test_a_failed_stop_list_is_remembered_only_briefly(
    client: TestClient,
    fake: FakeTdx,
    clock: FakeClock,
    stops_response: httpx.Response,
):
    fake.stops_response = stops_response
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    # Arrivals are still served, without coordinates.
    assert resp.status_code == 200
    assert len(resp.json()["stops"]) == 34
    assert all(s["position"] is None for s in resp.json()["stops"])
    assert fake.stop_requests == 1

    # TDX recovers, but within the minute it is not asked again...
    fake.stops_response = None
    clock.now += 59
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert all(s["position"] is None for s in resp.json()["stops"])
    assert fake.stop_requests == 1

    # ...and after it, it is, and the good list is then kept.
    clock.now += 2
    resp = client.get("/api/v1/transit/bus/132/arrivals")
    assert any(s["position"] is not None for s in resp.json()["stops"])
    assert fake.stop_requests == 2
    clock.now += 3600
    client.get("/api/v1/transit/bus/132/arrivals")
    assert fake.stop_requests == 2


def test_destinations_file_is_valid_and_ids_are_unique():
    loaded = destinations.load()
    assert len({d.id for d in loaded}) == len(loaded) == 5
    for destination in loaded:
        assert destination.sources, destination.id
        assert set(destination.bus_routes) <= set(tdx.ROUTES), destination.id


def test_destination_links_to_transit_directions_from_ncu(client: TestClient):
    body = client.get("/api/v1/transit/destinations/nia-taoyuan-service-center").json()
    assert body["address"]["zh"] == "桃園市桃園區縣府路106號1樓"
    assert body["maps_url"] == (
        "https://www.google.com/maps/dir/?api=1&origin=24.9684%2C121.1916"
        "&destination=24.9943647%2C121.3027015&travelmode=transit"
    )


def test_destination_without_coordinates_links_by_address(client: TestClient):
    body = client.get("/api/v1/transit/destinations/nhi-northern-division").json()
    assert body["position"] is None
    assert "destination=%E6%A1%83%E5%9C%92%E5%B8%82" in body["maps_url"]


def test_destinations_filter_by_category(client: TestClient):
    url = "/api/v1/transit/destinations"
    assert len(client.get(url).json()) == 5
    body = client.get(url, params={"category": "station"}).json()
    assert [d["id"] for d in body] == ["zhongli-train-station", "thsr-taoyuan-station"]
    assert client.get(f"{url}/nowhere").status_code == 404
    assert client.get(url, params={"category": "x"}).status_code == 422
    # On-campus buildings live under /campus/places, not here.
    assert client.get(url, params={"category": "campus"}).status_code == 422


def test_the_old_places_paths_are_gone(client: TestClient):
    assert client.get("/api/v1/transit/places").status_code == 404


def test_destination_search_matches_names_in_both_languages():
    assert [d.id for d in destinations.search("高鐵")] == ["thsr-taoyuan-station"]
    assert [d.id for d in destinations.search("AIRPORT")] == ["taoyuan-airport"]
    assert [d.id for d in destinations.search("train station")] == [
        "zhongli-train-station"
    ]
    assert destinations.search("moon base") == []
