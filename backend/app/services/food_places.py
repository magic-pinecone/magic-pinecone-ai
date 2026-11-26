"""Places to eat around NCU, outside what campus_places lists.

The list lives in `app/data/food_places.json`, written by
scripts/fetch_food_places.py: halal-certified restaurants within 10 km from
the Tourism Administration, and restaurants and cafes within 2 km from
OpenStreetMap. Names are as the sources give them, mostly Chinese.
"""

import json
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel, Field, computed_field

from app.services.campus_places import MapLinks, map_links_to

DATA_FILE = Path(__file__).resolve().parent.parent / "data" / "food_places.json"


class HalalCertificate(BaseModel):
    # Certificate codes such as MR (Muslim Restaurant) or MFR (Muslim
    # Friendly Restaurant), as the Tourism Administration lists them.
    certificates: list[str] = Field(min_length=1)
    certifier: str


class FoodPlace(BaseModel):
    id: str
    name: str
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    # Cuisine or kind of place, in the source's words.
    kind: str | None
    osm_id: str | None
    # None means not checked, not that the place is not halal.
    halal: HalalCertificate | None
    address: str | None
    phone: str | None

    @computed_field
    @property
    def map_links(self) -> MapLinks:
        return map_links_to(self.latitude, self.longitude)


class FoodPlaceData(BaseModel):
    fetched: str
    attribution: str
    places: list[FoodPlace]


@lru_cache
def load() -> FoodPlaceData:
    data = FoodPlaceData.model_validate(
        json.loads(DATA_FILE.read_text(encoding="utf-8"))
    )
    ids = [place.id for place in data.places]
    if len(ids) != len(set(ids)):
        raise ValueError(f"Duplicate food place ids in {DATA_FILE.name}")
    return data


def search(halal_only: bool = False) -> list[FoodPlace]:
    return [place for place in load().places if place.halal or not halal_only]
