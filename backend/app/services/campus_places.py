"""Campus places: a small curated list of where things are at NCU.

The list lives in `app/data/campus_places.json`. Coordinates come from
OpenStreetMap; the English names are ours unless OpenStreetMap had one.
Each place carries links that open it in a maps app, so walking directions
are left to the phone instead of being computed here.
"""

import json
from functools import lru_cache
from pathlib import Path
from typing import Literal
from urllib.parse import urlencode

from pydantic import BaseModel, Field, computed_field

DATA_FILE = Path(__file__).resolve().parent.parent / "data" / "campus_places.json"

PlaceCategory = Literal[
    "academic",
    "administration",
    "library",
    "dormitory",
    "dining",
    "sports",
    "service",
    "transit",
    "landmark",
]


class MapLinks(BaseModel):
    google_maps: str
    google_walking_directions: str
    apple_maps: str


class CampusPlace(BaseModel):
    id: str
    name_zh: str
    name_en: str
    # NCU building code as used in classroom numbers, e.g. "E6".
    code: str | None
    category: PlaceCategory
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    # "<type>/<id>" of the OpenStreetMap object the coordinates came from.
    osm_id: str
    aliases: list[str]
    note_en: str | None

    @computed_field
    @property
    def map_links(self) -> MapLinks:
        point = f"{self.latitude},{self.longitude}"
        return MapLinks(
            google_maps="https://www.google.com/maps/search/?"
            + urlencode({"api": 1, "query": point}),
            google_walking_directions="https://www.google.com/maps/dir/?"
            + urlencode({"api": 1, "destination": point, "travelmode": "walking"}),
            apple_maps="https://maps.apple.com/?"
            + urlencode({"daddr": point, "dirflg": "w"}),
        )

    def matches(self, query: str) -> bool:
        needle = query.casefold()
        haystack = [self.name_zh, self.name_en, self.code or "", *self.aliases]
        return any(needle in text.casefold() for text in haystack)


class CampusPlaceData(BaseModel):
    source: str
    attribution: str
    places: list[CampusPlace]


@lru_cache
def load() -> CampusPlaceData:
    data = CampusPlaceData.model_validate(
        json.loads(DATA_FILE.read_text(encoding="utf-8"))
    )
    ids = [place.id for place in data.places]
    if len(ids) != len(set(ids)):
        raise ValueError(f"Duplicate place ids in {DATA_FILE.name}")
    return data


def search(
    query: str | None = None, category: PlaceCategory | None = None
) -> list[CampusPlace]:
    """Places matching every given filter, in file order. No filter returns all."""
    query = (query or "").strip()
    return [
        place
        for place in load().places
        if (category is None or place.category == category)
        and (not query or place.matches(query))
    ]


def get(place_id: str) -> CampusPlace | None:
    return next((place for place in load().places if place.id == place_id), None)
