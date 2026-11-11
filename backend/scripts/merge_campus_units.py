"""Write researched building units into app/data/campus_places.json.

    uv run python scripts/merge_campus_units.py units.json

`units.json` maps place ids to the units inside, in the shape documented for
`units` in docs/api-contract.md, plus an optional `note` per unit that is for
the reviewer and is dropped here. Units of a place that the file lists are
replaced; places it omits keep theirs.
"""

import json
import sys
from pathlib import Path

DATA_FILE = Path(__file__).resolve().parent.parent / "app" / "data" / "campus_places.json"
FIELDS = ("name_zh", "name_en", "name_vi", "kind", "aliases", "floor", "website", "sources")


def main(units_file: str) -> None:
    research = json.loads(Path(units_file).read_text(encoding="utf-8"))["buildings"]
    data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    places = {place["id"]: place for place in data["places"]}
    unknown = sorted(set(research) - set(places))
    if unknown:
        raise SystemExit(f"Not place ids in {DATA_FILE.name}: {', '.join(unknown)}")
    for place_id, units in research.items():
        places[place_id]["units"] = [
            {field: unit.get(field) for field in FIELDS}
            | {"aliases": unit.get("aliases") or [], "sources": unit["sources"]}
            for unit in units
        ]
    DATA_FILE.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote units for {len(research)} places")


if __name__ == "__main__":
    main(sys.argv[1])
