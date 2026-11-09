"""Transit endpoints: live bus arrivals around NCU, relayed from TDX."""

import logging
from datetime import UTC, datetime

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import tdx
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
    if campus_only:
        stops = [s for s in stops if s.on_campus]
    return BusArrivalsResponse(route=route, fetched_at=datetime.now(UTC), stops=stops)
