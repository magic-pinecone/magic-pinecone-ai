"""Fetch the NCU campus outlines from OpenStreetMap into app/data/campus_map.geojson.

The file holds the campus boundary, building footprints and sports grounds
inside it, so the app can draw buildings as shapes and open a place when one
is tapped. A footprint whose OSM id matches a place in campus_places.json
carries that place's id as `place_id`.

Run from backend/: `uv run python scripts/fetch_campus_osm.py`. The data is
© OpenStreetMap contributors under ODbL 1.0.
"""

import json
import urllib.parse
import urllib.request
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "app" / "data"
OUTPUT = DATA_DIR / "campus_map.geojson"
PLACES = DATA_DIR / "campus_places.json"

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
CAMPUS_RELATION = 18727079
# Overpass answers 406 to clients that send no User-Agent.
USER_AGENT = (
    "magic-pinecone-backend/0.1 (https://github.com/magic-pinecone/magic-pinecone-ai)"
)

QUERY = f"""
[out:json][timeout:90];
rel({CAMPUS_RELATION});
map_to_area->.campus;
(
  rel({CAMPUS_RELATION});
  way(area.campus)["building"];
  rel(area.campus)["building"];
  way(area.campus)["leisure"~"^(pitch|track|sports_centre|stadium|swimming_pool)$"];
);
out geom;
"""


def fetch() -> dict:
    request = urllib.request.Request(
        OVERPASS_URL,
        data=urllib.parse.urlencode({"data": QUERY}).encode(),
        headers={"User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def ring(geometry: list[dict]) -> list[list[float]]:
    points = [[round(p["lon"], 6), round(p["lat"], 6)] for p in geometry]
    if points and points[0] != points[-1]:
        points.append(points[0])
    return points


def polygons(element: dict) -> list[list[list[list[float]]]]:
    """Each outer ring as its own polygon; inner rings are dropped, which only
    loses courtyards at the scale the app draws."""
    if element["type"] == "way":
        return [[ring(element["geometry"])]]
    return [
        [ring(member["geometry"])]
        for member in element.get("members", [])
        if member["type"] == "way"
        and member.get("role") == "outer"
        and "geometry" in member
    ]


def kind(element: dict) -> str:
    if element["type"] == "relation" and element["id"] == CAMPUS_RELATION:
        return "campus"
    if "building" in element.get("tags", {}):
        return "building"
    return "sports"


def to_feature(element: dict, place_ids: dict[str, str]) -> dict | None:
    shapes = [shape for shape in polygons(element) if len(shape[0]) >= 4]
    if not shapes:
        return None
    tags = element.get("tags", {})
    osm_id = f"{element['type']}/{element['id']}"
    geometry = (
        {"type": "Polygon", "coordinates": shapes[0]}
        if len(shapes) == 1
        else {"type": "MultiPolygon", "coordinates": shapes}
    )
    return {
        "type": "Feature",
        "id": osm_id,
        "geometry": geometry,
        "properties": {
            "osm_id": osm_id,
            "kind": kind(element),
            "name": tags.get("name"),
            "name_en": tags.get("name:en"),
            "place_id": place_ids.get(osm_id),
        },
    }


def main() -> None:
    places = json.loads(PLACES.read_text(encoding="utf-8"))["places"]
    place_ids = {p["osm_id"]: p["id"] for p in places if p.get("osm_id")}
    elements = fetch()["elements"]
    features = [f for f in (to_feature(e, place_ids) for e in elements) if f]
    features.sort(key=lambda f: (f["properties"]["kind"] != "campus", f["id"]))
    collection = {
        "type": "FeatureCollection",
        "attribution": "© OpenStreetMap contributors, ODbL 1.0",
        "features": features,
    }
    OUTPUT.write_text(
        json.dumps(collection, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    linked = sum(1 for f in features if f["properties"]["place_id"])
    print(f"{len(features)} features, {linked} linked to a place -> {OUTPUT}")


if __name__ == "__main__":
    main()
