"""Transit endpoints: live bus arrivals around NCU, relayed from TDX, and how to
reach the off-campus destinations students need."""

import logging
from datetime import UTC, datetime

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import destinations, tdx
from app.services.destinations import DestinationCategory, DestinationResponse
from app.services.tdx import StopArrival

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/transit", tags=["transit"])

SOURCE = "TDX (tdx.transportdata.tw)"


class BusArrivalsResponse(BaseModel):
    route: str
    fetched_at: datetime
    source: str = SOURCE
    stops: list[StopArrival]


@router.get("/bus/routes", response_model=list[str])
def list_bus_routes() -> list[str]:
    """The bus routes that serve NCU and can be queried for arrivals."""
    return list(tdx.ROUTES)


@router.get("/bus/{route}/arrivals", response_model=BusArrivalsResponse)
def get_bus_arrivals(route: str, campus_only: bool = False) -> BusArrivalsResponse:
    """Estimated arrivals at each stop of a route, in route order."""
    if route not in tdx.ROUTES:
        raise HTTPException(status_code=404, detail=f"Unknown bus route {route}")
    try:
        stops = tdx.tdx_client.arrivals_with_positions(route, campus_only)
    except tdx.TdxNotConfiguredError as exc:
        raise HTTPException(
            status_code=503, detail="Bus arrivals are not configured on this server"
        ) from exc
    except tdx.TdxRateLimitedError as exc:
        logger.warning(f"TDX rate limited the request for route {route}")
        raise HTTPException(
            status_code=503,
            detail="TDX is rate limiting this server; try again shortly",
            headers={"Retry-After": str(exc.retry_after)},
        ) from exc
    except tdx.TdxError as exc:
        logger.warning(f"TDX request for route {route} failed: {exc}")
        raise HTTPException(
            status_code=502, detail="TDX is unreachable or sent an unusable answer"
        ) from exc
    return BusArrivalsResponse(route=route, fetched_at=datetime.now(UTC), stops=stops)


@router.get("/destinations", response_model=list[DestinationResponse])
def list_destinations(
    category: DestinationCategory | None = None,
) -> list[DestinationResponse]:
    """Off-campus places a student needs to reach, with how to get there.

    Buildings on the campus itself are under /campus/places.
    """
    return destinations.search(category=category)


@router.get("/destinations/{destination_id}", response_model=DestinationResponse)
def get_destination(destination_id: str) -> DestinationResponse:
    destination = destinations.get(destination_id)
    if destination is None:
        raise HTTPException(status_code=404, detail="Destination not found")
    return destination
