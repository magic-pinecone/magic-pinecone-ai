"""Off-campus destinations an international student needs to reach from NCU.

Train and high-speed-rail stations, the airport, the immigration office and
the health-insurance office, kept by hand in app/data/destinations.json so
they can be corrected without touching code. Every entry names where its
facts came from and when they were checked.

Buildings on the campus itself are a separate list: see campus_places.py.
"""

import json
from datetime import date
from functools import cache
from pathlib import Path
from typing import Literal
from urllib.parse import urlencode

from pydantic import BaseModel, Field, TypeAdapter

from app.services.localized import LocalizedName

DATA_FILE = Path(__file__).resolve().parent.parent / "data" / "destinations.json"

# Where Google Maps starts its directions: the middle of the NCU campus.
NCU_LAT, NCU_LON = 24.9684, 121.1916

DestinationCategory = Literal[
    "station", "airport", "immigration", "health_insurance", "hospital"
]


class Position(BaseModel):
    # WGS84, bounded to Taiwan so a swapped or TWD97 value is rejected on load.
    lat: float = Field(ge=21.5, le=25.5)
    lon: float = Field(ge=119.0, le=122.5)


class Destination(BaseModel):
    id: str
    category: DestinationCategory
    name: LocalizedName
    address: LocalizedName | None
    position: Position | None
    phone: str | None
    hours: LocalizedName | None
    official_url: str | None
    getting_there: LocalizedName
    # Routes that can be passed to the bus arrivals endpoint.
    bus_routes: list[str]
    sources: list[str]
    checked_on: date


class DestinationResponse(Destination):
    # Opens Google Maps with public-transport directions from NCU.
    maps_url: str


def maps_url(destination: Destination) -> str:
    if destination.position:
        target = f"{destination.position.lat},{destination.position.lon}"
    elif destination.address:
        target = destination.address.zh
    else:
        target = destination.name.zh
    query = urlencode(
        {
            "api": 1,
            "origin": f"{NCU_LAT},{NCU_LON}",
            "destination": target,
            "travelmode": "transit",
        }
    )
    return f"https://www.google.com/maps/dir/?{query}"


@cache
def load() -> list[DestinationResponse]:
    destinations = TypeAdapter(list[Destination]).validate_python(
        json.loads(DATA_FILE.read_text(encoding="utf-8"))
    )
    return [
        DestinationResponse(**d.model_dump(), maps_url=maps_url(d))
        for d in destinations
    ]


def get(destination_id: str) -> DestinationResponse | None:
    return next((d for d in load() if d.id == destination_id), None)


def search(
    query: str | None = None, category: DestinationCategory | None = None
) -> list[DestinationResponse]:
    """Destinations whose id, names or address hold every word of the query."""
    words = (query or "").casefold().split()
    found = []
    for destination in load():
        if category not in (None, destination.category):
            continue
        address = destination.address
        text = " ".join(
            [
                destination.id,
                destination.category,
                destination.name.zh,
                destination.name.en,
                address.zh if address else "",
                address.en if address else "",
            ]
        ).casefold()
        if all(word in text for word in words):
            found.append(destination)
    return found
