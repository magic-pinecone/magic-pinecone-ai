"""TDX (Transport Data eXchange) client for live bus arrivals around NCU.

TDX hands out a day-long token for a client id/secret pair and bills every data
call against a quota, so the token is kept until it expires and each route's
arrivals are reused for a few seconds.
"""

import time
from datetime import datetime
from typing import Any, Literal

import httpx
from pydantic import BaseModel

from app.core.config import settings

TOKEN_URL = (
    "https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token"
)
API_BASE = "https://tdx.transportdata.tw/api/basic/v2/Bus"

# The routes this API will ask TDX about, and where TDX files each of them.
# Only 132 has been checked against a real response so far.
ROUTE_SCOPES: dict[str, str] = {
    "132": "City/Taoyuan",
    "133": "City/Taoyuan",
    "172": "City/Taoyuan",
    "173": "City/Taoyuan",
    "9025A": "InterCity",
}

# Stops inside or at the gates of NCU. TDX's own English names for these are
# inconsistent ("The Front Door of Central University"), so they are replaced.
CAMPUS_STOPS: dict[str, str] = {
    "TAO269": "NCU Main Gate",
    "TAO270": "NCU Guard Station",
    "TAO2619": "NCU Lake",
    "TAO272": "NCU Yiren Hall (Gym)",
    "TAO273": "NCU Back Gate",
    "TAO274": "NCU Observation Deck",
    "TAO275": "NCU Main Gate",
}

ArrivalStatus = Literal[
    "ARRIVING", "NOT_DEPARTED", "SKIPPED", "LAST_BUS_GONE", "NO_SERVICE", "UNKNOWN"
]
_STOP_STATUS: dict[int, ArrivalStatus] = {
    0: "ARRIVING",
    1: "NOT_DEPARTED",
    2: "SKIPPED",
    3: "LAST_BUS_GONE",
    4: "NO_SERVICE",
}


class TdxNotConfiguredError(RuntimeError):
    pass


class LocalizedName(BaseModel):
    zh: str
    en: str


class StopPosition(BaseModel):
    # WGS84, as TDX publishes it.
    lat: float
    lon: float


class StopArrival(BaseModel):
    stop_uid: str
    stop_name: LocalizedName
    # None when TDX's stop list could not be read or has no usable coordinates.
    position: StopPosition | None = None
    # Position of the stop along the route. Loop routes such as 132 pass the same
    # place twice under one direction, so this is what tells the two apart.
    sequence: int
    direction: int
    on_campus: bool
    status: ArrivalStatus
    # Seconds until the bus reaches this stop; None when no bus is on its way.
    estimate_seconds: int | None
    # The next scheduled departure, which TDX reports when no bus is running.
    next_bus_time: datetime | None
    plate_number: str | None
    updated_at: datetime | None


def parse_arrivals(raw: list[dict[str, Any]]) -> list[StopArrival]:
    """Turn TDX's EstimatedTimeOfArrival rows into arrivals in route order."""
    arrivals = []
    for row in raw:
        uid = row.get("StopUID", "")
        name = row.get("StopName") or {}
        zh = name.get("Zh_tw", "")
        estimate = row.get("EstimateTime")
        arrivals.append(
            StopArrival(
                stop_uid=uid,
                stop_name=LocalizedName(
                    zh=zh, en=CAMPUS_STOPS.get(uid) or name.get("En") or zh
                ),
                sequence=row.get("StopSequence", 0),
                direction=row.get("Direction", 0),
                on_campus=uid in CAMPUS_STOPS,
                status=_STOP_STATUS.get(row.get("StopStatus", -1), "UNKNOWN"),
                estimate_seconds=estimate if isinstance(estimate, int) else None,
                next_bus_time=row.get("NextBusTime"),
                # -1 and "" both mean no bus is assigned to this stop yet.
                plate_number=row.get("PlateNumb")
                if row.get("PlateNumb") not in ("", "-1", None)
                else None,
                updated_at=row.get("UpdateTime"),
            )
        )
    return sorted(arrivals, key=lambda a: (a.direction, a.sequence))


def parse_stop_positions(raw: list[dict[str, Any]]) -> dict[str, StopPosition]:
    """Read each stop's coordinates out of TDX's StopOfRoute rows."""
    positions = {}
    for route in raw:
        for stop in route.get("Stops") or []:
            point = stop.get("StopPosition") or {}
            lat, lon = point.get("PositionLat"), point.get("PositionLon")
            # Skip blanks and anything outside Taiwan, e.g. 0,0 placeholders.
            if (
                isinstance(lat, int | float)
                and isinstance(lon, int | float)
                and 21.5 <= lat <= 25.5
                and 119.0 <= lon <= 122.5
            ):
                positions[stop.get("StopUID", "")] = StopPosition(lat=lat, lon=lon)
    return positions


class TdxClient:
    def __init__(self, transport: httpx.BaseTransport | None = None):
        self._transport = transport
        self._token: str | None = None
        self._token_expires_at = 0.0
        self._arrivals: dict[str, tuple[float, list[StopArrival]]] = {}
        self._positions: dict[str, tuple[float, dict[str, StopPosition]]] = {}

    def _http(self) -> httpx.Client:
        return httpx.Client(transport=self._transport, timeout=10.0)

    def _fetch_token(self, http: httpx.Client) -> str:
        if not (settings.TDX_CLIENT_ID and settings.TDX_CLIENT_SECRET):
            raise TdxNotConfiguredError(
                "TDX_CLIENT_ID and TDX_CLIENT_SECRET are not set"
            )
        resp = http.post(
            TOKEN_URL,
            data={
                "grant_type": "client_credentials",
                "client_id": settings.TDX_CLIENT_ID,
                "client_secret": settings.TDX_CLIENT_SECRET,
            },
        )
        resp.raise_for_status()
        body = resp.json()
        self._token = body["access_token"]
        # Renew a minute early so a request never goes out with a dying token.
        self._token_expires_at = time.monotonic() + body.get("expires_in", 3600) - 60
        return self._token

    def _get(self, http: httpx.Client, url: str) -> httpx.Response:
        token = self._token
        if token is None or time.monotonic() >= self._token_expires_at:
            token = self._fetch_token(http)
        params = {"$format": "JSON"}
        resp = http.get(
            url, params=params, headers={"Authorization": f"Bearer {token}"}
        )
        if resp.status_code == 401:
            # TDX revoked the token before its stated expiry: get a new one once.
            token = self._fetch_token(http)
            resp = http.get(
                url, params=params, headers={"Authorization": f"Bearer {token}"}
            )
        resp.raise_for_status()
        return resp

    def _rows(self, kind: str, route: str) -> list[dict[str, Any]]:
        with self._http() as http:
            resp = self._get(http, f"{API_BASE}/{kind}/{ROUTE_SCOPES[route]}/{route}")
        # TDX matches route names by prefix, so "132" can also return "132A".
        return [
            r
            for r in resp.json()
            if (r.get("RouteName") or {}).get("Zh_tw", route) == route
        ]

    def stop_positions(self, route: str) -> dict[str, StopPosition]:
        """Coordinates of a route's stops by StopUID. Stops rarely move."""
        cached = self._positions.get(route)
        max_age = settings.TDX_STOP_CACHE_HOURS * 3600
        if cached and time.monotonic() - cached[0] < max_age:
            return cached[1]
        positions = parse_stop_positions(self._rows("StopOfRoute", route))
        self._positions[route] = (time.monotonic(), positions)
        return positions

    def bus_arrivals(self, route: str) -> list[StopArrival]:
        """Arrivals at every stop of a route. Raises KeyError for an unlisted one."""
        if route not in ROUTE_SCOPES:
            raise KeyError(route)
        cached = self._arrivals.get(route)
        if cached and time.monotonic() - cached[0] < settings.TDX_ARRIVAL_CACHE_SECONDS:
            return cached[1]
        rows = self._rows("EstimatedTimeOfArrival", route)
        arrivals = parse_arrivals(rows)
        self._arrivals[route] = (time.monotonic(), arrivals)
        return arrivals


tdx_client = TdxClient()
