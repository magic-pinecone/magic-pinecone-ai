"""Campus places: a small curated list of where things are at NCU.

The list lives in `app/data/campus_places.json`. Building codes and the
Chinese names of coded buildings follow the NCU course system, coordinates
come from OpenStreetMap, and the English names are ours unless OpenStreetMap
had one.
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
    # Building code from the NCU course system, the prefix of a classroom
    # number such as "E6-A203". None for places that hold no classes.
    code: str | None
    category: PlaceCategory
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    # "<type>/<id>" of the OpenStreetMap object the coordinates came from.
    # None when the point was located by hand.
    osm_id: str | None
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

    def match_rank(self, query: str) -> int | None:
        """How well `query` fits: 0 the whole of a name, code or alias, 1 the
        start of one, 2 somewhere inside one, None no match."""
        needle = query.casefold()
        ranks = [
            0 if text == needle else 1 if text.startswith(needle) else 2
            for text in (
                value.casefold()
                for value in (
                    self.name_zh,
                    self.name_en,
                    self.code or "",
                    *self.aliases,
                )
            )
            if needle in text
        ]
        return min(ranks, default=None)

    def matches(self, query: str) -> bool:
        return self.match_rank(query) is not None


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
    """Places matching every given filter. No filter returns all.

    With a query the closest matches come first, so the building whose code is
    exactly "E" precedes every place that merely has an "e" in its name; ties
    and unfiltered lists keep file order.
    """
    query = (query or "").strip()
    places = [
        place
        for place in load().places
        if category is None or place.category == category
    ]
    if not query:
        return places
    ranked = [(place.match_rank(query), place) for place in places]
    return [
        place
        for _, place in sorted(
            (pair for pair in ranked if pair[0] is not None), key=lambda pair: pair[0]
        )
    ]


def get(place_id: str) -> CampusPlace | None:
    return next((place for place in load().places if place.id == place_id), None)
