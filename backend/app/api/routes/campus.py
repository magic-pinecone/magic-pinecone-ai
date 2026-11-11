"""Campus endpoints: where buildings and resources are, with links to a maps app."""

from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from app.services import campus_places
from app.services.campus_places import CampusPlace, PlaceCategory

router = APIRouter(prefix="/campus", tags=["campus"])


class PlacesResponse(BaseModel):
    attribution: str
    places: list[CampusPlace]


@router.get("/places", response_model=PlacesResponse)
def list_places(q: str | None = None, category: PlaceCategory | None = None):
    """Return campus places, optionally filtered by a name, code, or alias and by category."""
    return PlacesResponse(
        attribution=campus_places.load().attribution,
        places=campus_places.search(q, category),
    )


@router.get("/map.geojson")
def get_map() -> JSONResponse:
    """Return campus outlines as a GeoJSON FeatureCollection. A feature whose
    `place_id` is set opens that place from GET /campus/places."""
    return JSONResponse(campus_places.load_map(), media_type="application/geo+json")


@router.get("/places/{place_id}", response_model=CampusPlace)
def get_place(place_id: str):
    place = campus_places.get(place_id)
    if place is None:
        raise HTTPException(status_code=404, detail="No such place")
    return place
