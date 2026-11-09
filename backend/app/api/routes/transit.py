"""Transit endpoints: live bus arrivals around NCU, relayed from TDX."""

import logging
from datetime import UTC, datetime

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import places, tdx
from app.services.places import PlaceCategory, PlaceResponse
from app.services.tdx import StopArrival

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/transit", tags=["transit"])


class BusArrivalsResponse(BaseModel):
    route: str
    fetched_at: datetime
    source: str = "TDX (tdx.transportdata.tw)"
    stops: list[StopArrival]


@router.get("/bus/routes", response_model=list[str])
def list_bus_routes() -> list[str]:
    """The bus routes that serve NCU and can be queried for arrivals."""
    return list(tdx.ROUTE_SCOPES)


@router.get("/bus/{route}/arrivals", response_model=BusArrivalsResponse)
def get_bus_arrivals(route: str, campus_only: bool = False) -> BusArrivalsResponse:
    """Estimated arrivals at each stop of a route, in route order."""
    if route not in tdx.ROUTE_SCOPES:
        raise HTTPException(status_code=404, detail=f"Unknown bus route {route}")
    try:
        stops = tdx.tdx_client.bus_arrivals(route)
    except tdx.TdxNotConfiguredError as exc:
        raise HTTPException(
            status_code=503, detail="Bus arrivals are not configured on this server"
        ) from exc
    except httpx.HTTPError as exc:
        logger.warning(f"TDX request for route {route} failed: {exc}")
        raise HTTPException(status_code=502, detail="TDX is unreachable") from exc
    try:
        positions = tdx.tdx_client.stop_positions(route)
    except httpx.HTTPError as exc:
        # Arrivals are still worth returning without coordinates.
        logger.warning(f"TDX stop list for route {route} failed: {exc}")
        positions = {}
    stops = [
        s.model_copy(update={"position": positions.get(s.stop_uid)}) for s in stops
    ]
    if campus_only:
        stops = [s for s in stops if s.on_campus]
    return BusArrivalsResponse(route=route, fetched_at=datetime.now(UTC), stops=stops)


@router.get("/places", response_model=list[PlaceResponse])
def list_places(category: PlaceCategory | None = None) -> list[PlaceResponse]:
    """Places an international student needs to reach, with how to get there."""
    return [p for p in places.load_places() if category in (None, p.category)]


@router.get("/places/{place_id}", response_model=PlaceResponse)
def get_place(place_id: str) -> PlaceResponse:
    for place in places.load_places():
        if place.id == place_id:
            return place
    raise HTTPException(status_code=404, detail="Place not found")
