"""Places an international student needs to reach from NCU, kept by hand.

The list lives in app/data/places.json so it can be corrected without touching
code. Every entry names where its facts came from and when they were checked.
"""

import json
from datetime import date
from functools import cache
from pathlib import Path
from typing import Literal
from urllib.parse import urlencode

from pydantic import BaseModel, Field, TypeAdapter

from app.services.tdx import LocalizedName

PLACES_FILE = Path(__file__).parent.parent / "data" / "places.json"

# Where Google Maps starts its directions: the middle of the NCU campus.
NCU_LAT, NCU_LON = 24.9684, 121.1916

PlaceCategory = Literal[
    "station", "airport", "immigration", "health_insurance", "hospital", "campus"
]


class Position(BaseModel):
    # WGS84, bounded to Taiwan so a swapped or TWD97 value is rejected on load.
    lat: float = Field(ge=21.5, le=25.5)
    lon: float = Field(ge=119.0, le=122.5)


class Place(BaseModel):
    id: str
    category: PlaceCategory
    name: LocalizedName
    address: LocalizedName | None
    position: Position | None
    phone: str | None
    hours: LocalizedName | None
    official_url: str | None
    getting_there: LocalizedName
    bus_routes: list[str]
    sources: list[str]
    checked_on: date


class PlaceResponse(Place):
    # Opens Google Maps with public-transport directions from NCU.
    maps_url: str


def maps_url(place: Place) -> str:
    if place.position:
        destination = f"{place.position.lat},{place.position.lon}"
    elif place.address:
        destination = place.address.zh
    else:
        destination = place.name.zh
    query = urlencode(
        {
            "api": 1,
            "origin": f"{NCU_LAT},{NCU_LON}",
            "destination": destination,
            "travelmode": "transit",
        }
    )
    return f"https://www.google.com/maps/dir/?{query}"


@cache
def load_places() -> list[PlaceResponse]:
    places = TypeAdapter(list[Place]).validate_python(
        json.loads(PLACES_FILE.read_text(encoding="utf-8"))
    )
    return [PlaceResponse(**p.model_dump(), maps_url=maps_url(p)) for p in places]
