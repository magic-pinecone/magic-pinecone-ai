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

from pydantic import BaseModel, Field, computed_field, model_validator

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
    "shop",
]

PlaceTag = Literal[
    "vegetarian",
    "halal",
    "open_24h",
    "late_night",
    "cash_only",
    "card_accepted",
    "english_menu",
]


UnitKind = Literal["college", "department", "institute", "office", "center"]


class LocalizedText(BaseModel):
    zh: str
    en: str
    vi: str


class PriceRange(BaseModel):
    min: int = Field(ge=0)
    max: int = Field(ge=0)

    @model_validator(mode="after")
    def _min_not_above_max(self):
        if self.min > self.max:
            raise ValueError("price range min is above max")
        return self


class MapLinks(BaseModel):
    google_maps: str
    google_walking_directions: str
    apple_maps: str


def map_links_to(latitude: float, longitude: float) -> MapLinks:
    point = f"{latitude},{longitude}"
    return MapLinks(
        google_maps="https://www.google.com/maps/search/?"
        + urlencode({"api": 1, "query": point}),
        google_walking_directions="https://www.google.com/maps/dir/?"
        + urlencode({"api": 1, "destination": point, "travelmode": "walking"}),
        apple_maps="https://maps.apple.com/?"
        + urlencode({"daddr": point, "dirflg": "w"}),
    )


class CampusUnit(BaseModel):
    """A college, department, office or center housed in a building."""

    name_zh: str
    name_en: str
    name_vi: str | None = None
    kind: UnitKind
    # Short names students use, such as "資工系" or "CSIE".
    aliases: list[str] = []
    # Where inside the building as the source states it, such as "3F" or a
    # room number. None when the source gives none.
    floor: str | None = None
    website: str | None = None
    # URLs that state the unit is in this building.
    sources: list[str] = Field(min_length=1)

    def names(self) -> list[str]:
        return [self.name_zh, self.name_en, self.name_vi or "", *self.aliases]


class CampusPlace(BaseModel):
    id: str
    name_zh: str
    name_en: str
    # None until someone has translated the name.
    name_vi: str | None = None
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
    description: LocalizedText | None = None
    # OpenStreetMap opening_hours syntax, e.g. "Mo-Fr 08:00-17:00".
    opening_hours: str | None = None
    price_range_twd: PriceRange | None = None
    # Sample menu items with prices.
    price_note: LocalizedText | None = None
    tags: list[PlaceTag] = []
    # URLs the data came from.
    sources: list[str] = []
    # Id of the building this place is inside, such as a shop in a dining
    # hall. It shares the host's coordinates, so maps list it under the host.
    located_in: str | None = None
    # Units housed in this building, so a search for a department finds it.
    units: list[CampusUnit] = []

    @computed_field
    @property
    def map_links(self) -> MapLinks:
        return map_links_to(self.latitude, self.longitude)

    def match_rank(self, query: str) -> int | None:
        """How well `query` fits: 0 the whole of a name, code or alias, 1 the
        start of one, 2 somewhere inside one, None no match. The names of the
        units inside count one step lower, so 工程五館 itself outranks a
        building that only houses something called 工程."""
        own = _rank(
            query,
            [self.name_zh, self.name_en, self.name_vi or "", self.code or "", *self.aliases],
        )
        units = _rank(query, [name for unit in self.units for name in unit.names()])
        ranks = [rank for rank in (own, None if units is None else units + 1) if rank is not None]
        return min(ranks, default=None)

    def matches(self, query: str) -> bool:
        return self.match_rank(query) is not None


def _rank(query: str, values: list[str]) -> int | None:
    needle = query.casefold()
    ranks = [
        0 if text == needle else 1 if text.startswith(needle) else 2
        for text in (value.casefold() for value in values)
        if needle in text
    ]
    return min(ranks, default=None)


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
    hosts = {place.id: place for place in data.places}
    for place in data.places:
        host = hosts.get(place.located_in) if place.located_in else None
        if place.located_in and (host is None or host.located_in):
            raise ValueError(
                f"{place.id} is located in {place.located_in}, which is not a top-level place"
            )
    return data


MAP_FILE = DATA_FILE.with_name("campus_map.geojson")


@lru_cache
def load_map() -> dict:
    """The campus boundary, building footprints and sports grounds from
    OpenStreetMap, as written by scripts/fetch_campus_osm.py."""
    return json.loads(MAP_FILE.read_text(encoding="utf-8"))


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
