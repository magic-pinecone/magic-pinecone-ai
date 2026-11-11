#!/usr/bin/env python3
"""Refresh the food map's places around NCU.

Usage: python3 -I scripts/update_food_map.py

Halal places come from the Tourism Administration's list of certified restaurants, the only
source that names the certificate and who issued it. Everything else comes from OpenStreetMap.
"""

import datetime
import html
import json
import math
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

NCU = (24.9682, 121.1955)
# Certified places are sparse, so they are kept from further away than ordinary ones.
HALAL_KM = 10
NEARBY_M = 2000
HALAL_URL = "https://www.taiwan.net.tw/m1.aspx?sNo=0020118&page={page}"
OVERPASS_URLS = ("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
OVERPASS_QUERY = (
    '[out:json][timeout:25];nwr["amenity"~"^(restaurant|cafe|fast_food|food_court)$"]["name"]'
    f"(around:{NEARBY_M},{NCU[0]},{NCU[1]});out center tags;"
)
OUTPUT = Path(__file__).resolve().parent.parent / "shared/src/commonMain/moko-resources/files/ncu_food_places.json"


def fetch(url: str, data: bytes | None = None, agent: str = "Mozilla/5.0") -> str:
    request = urllib.request.Request(url, data=data, headers={"User-Agent": agent})
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read().decode("utf-8", errors="replace")


def km_from_ncu(lat: float, lon: float) -> float:
    haversine = (
        math.sin(math.radians(lat - NCU[0]) / 2) ** 2
        + math.cos(math.radians(NCU[0])) * math.cos(math.radians(lat)) * math.sin(math.radians(lon - NCU[1]) / 2) ** 2
    )
    return 6371 * 2 * math.asin(math.sqrt(haversine))


def text(fragment: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", fragment)).strip()


def halal_places() -> list[dict]:
    places = {}
    page = 1
    while True:
        try:
            rows = re.findall(r"<tbody>(.*?)</tbody>", fetch(HALAL_URL.format(page=page)), flags=re.S)
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
                "lat": round(lat, 6),
                "lon": round(lon, 6),
                "kind": text(cells[3]),
                "certificates": certificates,
                "certifier": text(cells[4]),
                "address": address,
                "phone": text(phone[1]) if phone else "",
            }
        page += 1
    return sorted(places.values(), key=lambda place: km_from_ncu(place["lat"], place["lon"]))


def nearby_places() -> list[dict]:
    body = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode()
    # The public servers are often at capacity, so each gets a few turns.
    for url in OVERPASS_URLS * 4:
        try:
            # Overpass refuses a browser's user agent; the tourism site refuses anything else.
            elements = json.loads(fetch(url, body, agent="ncu-food-map-refresh/1.0"))["elements"]
            break
        except (OSError, ValueError):
            time.sleep(10)
    else:
        raise SystemExit("No Overpass server answered; try again later.")
    places = []
    for element in elements:
        tags = element["tags"]
        position = element.get("center", element)
        places.append(
            {
                "name": tags["name"],
                "lat": round(position["lat"], 6),
                "lon": round(position["lon"], 6),
                "kind": tags.get("cuisine", tags["amenity"]).replace("_", " ").replace(";", ", "),
            }
        )
    return sorted(places, key=lambda place: (place["name"], place["lat"]))


def main() -> None:
    halal, nearby = halal_places(), nearby_places()
    assert halal, "the halal list parsed to nothing; the page layout probably changed"
    assert all(km_from_ncu(place["lat"], place["lon"]) <= HALAL_KM for place in halal)
    assert len(nearby) > 20, f"only {len(nearby)} places from OpenStreetMap"
    data = {"fetched": datetime.date.today().isoformat(), "center": NCU, "halal": halal, "nearby": nearby}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(halal)} halal and {len(nearby)} nearby places -> {OUTPUT}")


if __name__ == "__main__":
    main()
