"""TDX (Transport Data eXchange) client for live bus arrivals around NCU.

TDX hands out a day-long token for a client id/secret pair and bills every data
call against a quota, so the token is kept until it expires and each route's
arrivals are reused for a few seconds. Endpoints run on a thread pool, so the
token and the caches are guarded by locks.
"""

import logging
import threading
import time
from collections.abc import Callable
from datetime import datetime
from typing import Any, Literal, NamedTuple

import httpx
from pydantic import BaseModel, ValidationError

from app.core.config import settings
from app.services.localized import LocalizedName

logger = logging.getLogger(__name__)

TOKEN_URL = (
    "https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token"
)
API_BASE = "https://tdx.transportdata.tw/api/basic/v2/Bus"

# How long an empty or failed stop list is remembered, so that a rate-limited
# TDX is not asked again on every request.
STOP_FAILURE_CACHE_SECONDS = 60
# What a 429 without a usable Retry-After header is answered with.
DEFAULT_RETRY_AFTER_SECONDS = 60


class BusRoute(NamedTuple):
    # Where TDX files the route: "City/<city>" or "InterCity".
    scope: str
    # The RouteName to ask TDX for.
    tdx_name: str
    # Set when the route is one branch of a TDX route; rows are then kept only
    # when their SubRouteName matches.
    sub_route: str | None = None


# The routes this API will ask TDX about, keyed by the number riders know.
# 9025A is filed under InterCity route 9025 as the sub-route "9025A".
ROUTES: dict[str, BusRoute] = {
    "132": BusRoute("City/Taoyuan", "132"),
    "133": BusRoute("City/Taoyuan", "133"),
    "172": BusRoute("City/Taoyuan", "172"),
    "173": BusRoute("City/Taoyuan", "173"),
    "9025A": BusRoute("InterCity", "9025", "9025A"),
}

# Stops inside or at the gates of NCU are recognised by their Chinese name:
# every route has its own StopUIDs for the same kerb, but the names agree.
CAMPUS_NAME_MARKERS = ("中央大學", "中大湖")

# TDX's own English names for the campus stops differ from route to route
# ("The Front Door of Central University"), so they are replaced.
CAMPUS_STOP_NAMES_EN: dict[str, str] = {
    "中央大學正門": "NCU Main Gate",
    "中央大學警衛室": "NCU Guard Station",
    "中大湖": "NCU Lake",
    "中央大學依仁堂": "NCU Yiren Hall (Gym)",
    "中央大學後門": "NCU Back Gate",
    "中央大學觀景台": "NCU Observation Deck",
}

ArrivalStatus = Literal[
    "EN_ROUTE", "NOT_DEPARTED", "SKIPPED", "LAST_BUS_GONE", "NO_SERVICE", "UNKNOWN"
]
# TDX's StopStatus. 0 only says the stop is being served normally; how soon the
# bus comes is in the estimate.
_STOP_STATUS: dict[int, ArrivalStatus] = {
    0: "EN_ROUTE",
    1: "NOT_DEPARTED",
    2: "SKIPPED",
    3: "LAST_BUS_GONE",
    4: "NO_SERVICE",
}


class TdxError(RuntimeError):
    pass


class TdxNotConfiguredError(TdxError):
    pass


class TdxRateLimitedError(TdxError):
    def __init__(self, retry_after: int):
        super().__init__(f"TDX is rate limiting; retry in {retry_after} s")
        self.retry_after = retry_after


class TdxUpstreamError(TdxError):
    """TDX could not be reached or answered with something unusable."""


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


def is_campus_stop(name_zh: str) -> bool:
    return any(marker in name_zh for marker in CAMPUS_NAME_MARKERS)


def parse_arrivals(raw: list[dict[str, Any]]) -> list[StopArrival]:
    """Turn TDX's EstimatedTimeOfArrival rows into arrivals in route order."""
    arrivals = []
    for row in raw:
        name = row.get("StopName") or {}
        zh = name.get("Zh_tw") or ""
        estimate = row.get("EstimateTime")
        try:
            arrival = StopArrival(
                stop_uid=row.get("StopUID", ""),
                stop_name=LocalizedName(
                    zh=zh, en=CAMPUS_STOP_NAMES_EN.get(zh) or name.get("En") or zh
                ),
                sequence=row.get("StopSequence", 0),
                direction=row.get("Direction", 0),
                on_campus=is_campus_stop(zh),
                status=_STOP_STATUS.get(row.get("StopStatus", -1), "UNKNOWN"),
                estimate_seconds=estimate if isinstance(estimate, int) else None,
                next_bus_time=row.get("NextBusTime"),
                # -1 and "" both mean no bus is assigned to this stop yet.
                plate_number=row.get("PlateNumb")
                if row.get("PlateNumb") not in ("", "-1", None)
                else None,
                updated_at=row.get("UpdateTime"),
            )
        except (ValidationError, TypeError) as exc:
            raise TdxUpstreamError(f"TDX sent an unreadable arrival: {exc}") from exc
        arrivals.append(arrival)
    return sorted(arrivals, key=lambda a: (a.direction, a.sequence))


def parse_stop_positions(raw: list[dict[str, Any]]) -> dict[str, StopPosition]:
    """Read each stop's coordinates out of TDX's StopOfRoute rows."""
    positions = {}
    for route in raw:
        stops = route.get("Stops")
        for stop in stops if isinstance(stops, list) else []:
            if not isinstance(stop, dict):
                continue
            point = stop.get("StopPosition")
            if not isinstance(point, dict):
                continue
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


def _name_zh(row: dict[str, Any], field: str) -> str | None:
    name = row.get(field)
    return name.get("Zh_tw") if isinstance(name, dict) else None


def _retry_after(resp: httpx.Response) -> int:
    try:
        seconds = int(resp.headers.get("Retry-After", ""))
    except ValueError:
        return DEFAULT_RETRY_AFTER_SECONDS
    return min(max(seconds, 1), 3600)


class TdxClient:
    def __init__(
        self,
        transport: httpx.BaseTransport | None = None,
        clock: Callable[[], float] = time.monotonic,
    ):
        self._transport = transport
        self._clock = clock
        self._token: str | None = None
        self._token_expires_at = 0.0
        self._token_lock = threading.Lock()
        # Both caches hold (expires_at, value).
        self._arrivals: dict[str, tuple[float, list[StopArrival]]] = {}
        self._positions: dict[str, tuple[float, dict[str, StopPosition]]] = {}
        # One lock per cache entry, held while it is being refilled, so that
        # simultaneous requests for a route cost a single TDX call.
        self._entry_locks: dict[str, threading.Lock] = {}
        self._entry_locks_guard = threading.Lock()

    def _http(self) -> httpx.Client:
        return httpx.Client(transport=self._transport, timeout=10.0)

    def _entry_lock(self, key: str) -> threading.Lock:
        with self._entry_locks_guard:
            return self._entry_locks.setdefault(key, threading.Lock())

    @staticmethod
    def _send(http: httpx.Client, method: str, url: str, **kwargs) -> httpx.Response:
        try:
            resp = http.request(method, url, **kwargs)
        except httpx.HTTPError as exc:
            raise TdxUpstreamError(f"TDX is unreachable: {exc!r}") from exc
        if resp.status_code == 429:
            raise TdxRateLimitedError(_retry_after(resp))
        return resp

    def _fetch_token(self, http: httpx.Client) -> str:
        if not (settings.TDX_CLIENT_ID and settings.TDX_CLIENT_SECRET):
            raise TdxNotConfiguredError(
                "TDX_CLIENT_ID and TDX_CLIENT_SECRET are not set"
            )
        resp = self._send(
            http,
            "POST",
            TOKEN_URL,
            data={
                "grant_type": "client_credentials",
                "client_id": settings.TDX_CLIENT_ID,
                "client_secret": settings.TDX_CLIENT_SECRET,
            },
        )
        if not resp.is_success:
            raise TdxUpstreamError(f"TDX token request answered {resp.status_code}")
        try:
            body = resp.json()
            token = body["access_token"]
            lifetime = float(body.get("expires_in", 3600))
        except (ValueError, TypeError, KeyError) as exc:
            raise TdxUpstreamError("TDX token response is unreadable") from exc
        if not isinstance(token, str) or not token:
            raise TdxUpstreamError("TDX token response has no access token")
        self._token = token
        # Renew a minute early so a request never goes out with a dying token.
        self._token_expires_at = self._clock() + lifetime - 60
        return token

    def _bearer(self, http: httpx.Client, rejected: str | None = None) -> str:
        """The current token, renewed when it is expired or was just rejected."""
        with self._token_lock:
            token = self._token
            if (
                token is None
                or token == rejected
                or self._clock() >= self._token_expires_at
            ):
                token = self._fetch_token(http)
            return token

    def _get_json(self, http: httpx.Client, url: str) -> Any:
        params = {"$format": "JSON"}
        token = self._bearer(http)
        resp = self._send(
            http,
            "GET",
            url,
            params=params,
            headers={"Authorization": f"Bearer {token}"},
        )
        if resp.status_code == 401:
            # TDX revoked the token before its stated expiry: get a new one once.
            token = self._bearer(http, rejected=token)
            resp = self._send(
                http,
                "GET",
                url,
                params=params,
                headers={"Authorization": f"Bearer {token}"},
            )
        if not resp.is_success:
            raise TdxUpstreamError(f"TDX answered {resp.status_code}")
        try:
            return resp.json()
        except ValueError as exc:
            raise TdxUpstreamError("TDX did not answer with JSON") from exc

    def _rows(
        self, kind: str, route: str, match_sub_route: bool
    ) -> list[dict[str, Any]]:
        spec = ROUTES[route]
        with self._http() as http:
            body = self._get_json(
                http, f"{API_BASE}/{kind}/{spec.scope}/{spec.tdx_name}"
            )
        if not isinstance(body, list) or not all(isinstance(r, dict) for r in body):
            raise TdxUpstreamError("TDX did not answer with a list of rows")
        # TDX matches route names by prefix, so "132" can also return "132A".
        rows = [r for r in body if _name_zh(r, "RouteName") in (None, spec.tdx_name)]
        if spec.sub_route and match_sub_route:
            rows = [r for r in rows if _name_zh(r, "SubRouteName") == spec.sub_route]
        return rows

    def stop_positions(self, route: str) -> dict[str, StopPosition]:
        """Coordinates of a route's stops by StopUID; empty when TDX has none.

        Stops rarely move, so a good list is kept for TDX_STOP_CACHE_HOURS. A
        failed or empty one is kept only briefly and then asked for again.
        """
        with self._entry_lock(f"positions/{route}"):
            cached = self._positions.get(route)
            if cached and self._clock() < cached[0]:
                return cached[1]
            try:
                # Every branch of a route is read: positions are looked up by
                # StopUID, so stops of another branch do no harm.
                positions = parse_stop_positions(
                    self._rows("StopOfRoute", route, match_sub_route=False)
                )
            except TdxNotConfiguredError:
                raise
            except TdxError as exc:
                logger.warning(f"TDX stop list for route {route} failed: {exc}")
                positions = {}
            max_age = (
                settings.TDX_STOP_CACHE_HOURS * 3600
                if positions
                else STOP_FAILURE_CACHE_SECONDS
            )
            self._positions[route] = (self._clock() + max_age, positions)
            return positions

    def bus_arrivals(self, route: str) -> list[StopArrival]:
        """Arrivals at every stop of a route. Raises KeyError for an unlisted one."""
        if route not in ROUTES:
            raise KeyError(route)
        with self._entry_lock(f"arrivals/{route}"):
            cached = self._arrivals.get(route)
            if cached and self._clock() < cached[0]:
                return cached[1]
            arrivals = parse_arrivals(
                self._rows("EstimatedTimeOfArrival", route, match_sub_route=True)
            )
            self._arrivals[route] = (
                self._clock() + settings.TDX_ARRIVAL_CACHE_SECONDS,
                arrivals,
            )
            return arrivals

    def arrivals_with_positions(
        self, route: str, campus_only: bool = False
    ) -> list[StopArrival]:
        """Arrivals with each stop's coordinates filled in where TDX has them."""
        stops = self.bus_arrivals(route)
        # Arrivals are still worth returning without coordinates.
        positions = self.stop_positions(route)
        return [
            s.model_copy(update={"position": positions.get(s.stop_uid)})
            for s in stops
            if s.on_campus or not campus_only
        ]


tdx_client = TdxClient()
