"""Refresh the places to eat around NCU in app/data/food_places.json.

Run from backend/: `uv run python scripts/fetch_food_places.py`.

Halal places come from the Tourism Administration's list of certified
restaurants, the only source that names the certificate and who issued it.
Everything else comes from OpenStreetMap, minus what campus_places.json
already lists. Ported from the food map in magic-pinecone-ai#41.
"""

import datetime
import html
import json
import math
import re
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "app" / "data"
OUTPUT = DATA_DIR / "food_places.json"
CAMPUS_PLACES = DATA_DIR / "campus_places.json"

NCU = (24.9682, 121.1955)
# Certified places are sparse, so they are kept from further away than ordinary ones.
HALAL_KM = 10
NEARBY_M = 2000
HALAL_URL = "https://www.taiwan.net.tw/m1.aspx?sNo=0020118&page={page}"
OVERPASS_URLS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
)
OVERPASS_QUERY = (
    '[out:json][timeout:25];nwr["amenity"~"^(restaurant|cafe|fast_food|food_court)$"]["name"]'
    f"(around:{NEARBY_M},{NCU[0]},{NCU[1]});out center tags;"
)


# The tourism site's certificate has no Subject Key Identifier, which Python
# 3.13's strict X.509 checks reject; the chain is still verified.
TLS = ssl.create_default_context()
TLS.verify_flags &= ~ssl.VERIFY_X509_STRICT


def fetch(url: str, data: bytes | None = None, agent: str = "Mozilla/5.0") -> str:
    request = urllib.request.Request(url, data=data, headers={"User-Agent": agent})
    with urllib.request.urlopen(request, timeout=90, context=TLS) as response:
        return response.read().decode("utf-8", errors="replace")


def km_from_ncu(lat: float, lon: float) -> float:
    haversine = (
        math.sin(math.radians(lat - NCU[0]) / 2) ** 2
        + math.cos(math.radians(NCU[0]))
        * math.cos(math.radians(lat))
        * math.sin(math.radians(lon - NCU[1]) / 2) ** 2
    )
    return 6371 * 2 * math.asin(math.sqrt(haversine))


def text(fragment: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", fragment)).strip()


def halal_places() -> list[dict]:
    places = {}
    page = 1
    while True:
        try:
            rows = re.findall(
                r"<tbody>(.*?)</tbody>", fetch(HALAL_URL.format(page=page)), flags=re.S
            )
        except urllib.error.HTTPError:
            # The page after the last one redirects to a 404.
            rows = []
        if not rows:
            break
        for row in rows:
            cells = re.findall(r"<td[^>]*data-th[^>]*>(.*?)</td>", row, flags=re.S)
            location = re.search(r"daddr=([\d.]+),([\d.]+)[^>]*>([^<]*)<", row)
            phone = re.search(r"電話：</span>([^<]*)<", row)
            if len(cells) < 5 or not location:
                continue
            certificates = re.findall(r"<span[^>]*>([A-Z]+)</span>", cells[1])
            lat, lon = float(location[1]), float(location[2])
            # HK is a kitchen that sells packaged food, not somewhere to eat.
            if km_from_ncu(lat, lon) > HALAL_KM or set(certificates) <= {"HK"}:
                continue
            address = text(location[3])
            places[text(cells[0]), address] = {
                "name": text(cells[0]),
                "latitude": round(lat, 6),
                "longitude": round(lon, 6),
                "kind": text(cells[3]) or None,
                "osm_id": None,
                "halal": {"certificates": certificates, "certifier": text(cells[4])},
                "address": address,
                "phone": text(phone[1]) if phone else None,
            }
        page += 1
    ordered = sorted(
        places.values(), key=lambda p: km_from_ncu(p["latitude"], p["longitude"])
    )
    return [{"id": f"halal-{index + 1}", **p} for index, p in enumerate(ordered)]


def nearby_places(known_osm_ids: set[str]) -> list[dict]:
    body = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode()
    # The public servers are often at capacity, so each gets a few turns.
    for url in OVERPASS_URLS * 4:
        try:
            # Overpass refuses a browser's user agent; the tourism site refuses anything else.
            elements = json.loads(fetch(url, body, agent="magic-pinecone-backend/0.1"))[
                "elements"
            ]
            break
        except (OSError, ValueError):
            time.sleep(10)
    else:
        raise SystemExit("No Overpass server answered; try again later.")
    places = []
    for element in elements:
        osm_id = f"{element['type']}/{element['id']}"
        if osm_id in known_osm_ids:
            continue
        tags = element["tags"]
        position = element.get("center", element)
        places.append(
            {
                "id": osm_id.replace("/", "-"),
                "name": tags["name"],
                "latitude": round(position["lat"], 6),
                "longitude": round(position["lon"], 6),
                "kind": tags.get("cuisine", tags["amenity"])
                .replace("_", " ")
                .replace(";", ", "),
                "osm_id": osm_id,
                "halal": None,
                "address": None,
                "phone": None,
            }
        )
    return sorted(places, key=lambda p: (p["name"], p["latitude"]))


def main() -> None:
    campus = json.loads(CAMPUS_PLACES.read_text(encoding="utf-8"))["places"]
    known = {p["osm_id"] for p in campus if p.get("osm_id")}
    halal, nearby = halal_places(), nearby_places(known)
    assert halal, "the halal list parsed to nothing; the page layout probably changed"
    assert len(nearby) > 20, f"only {len(nearby)} places from OpenStreetMap"
    data = {
        "fetched": datetime.date.today().isoformat(),
        "attribution": "© OpenStreetMap contributors, ODbL 1.0; "
        "halal certificates: Tourism Administration, MOTC",
        "places": halal + nearby,
    }
    OUTPUT.write_text(
        json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    print(f"{len(halal)} halal and {len(nearby)} nearby places -> {OUTPUT}")


if __name__ == "__main__":
    main()
