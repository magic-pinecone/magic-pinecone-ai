"""Pre-translate the government guides with Argos Translate, offline, into files the API ships.

Writes `app/data/gov_guides/translations/<code>.json` for en, vi and zt (Traditional Chinese), each mapping a
source line (title, agency or a line of text) to its translation. GET /api/v1/intl/guides?lang= serves these
first and asks the live LibreTranslate service only for lines they lack, so guides are translated even on a
server that cannot run LibreTranslate. These are the same open models LibreTranslate runs.

Lines already translated in the files are kept; only new lines are translated, and lines no guide uses any
more are dropped. Re-run after rebuilding the corpus. From backend/:

    uv run --no-project --python 3.12 --with argostranslate python -I scripts/translate_gov_guides.py

Argos has no Chinese<->Vietnamese model, so Chinese pivots through the English translation.
"""

import json
import os
import sys
from functools import cache
from pathlib import Path

# -I leaves the script's own directory off sys.path, so add backend/ for the app package.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
# Argos's default sentence splitter is stanza's torch model; MiniSBD is a small ONNX model. Read at import time.
os.environ.setdefault("ARGOS_CHUNK_TYPE", "MINISBD")
# Windows consoles default to a legacy code page that cannot print Vietnamese.
sys.stdout.reconfigure(encoding="utf-8")

import argostranslate.package  # noqa: E402
import argostranslate.translate  # noqa: E402

from app.services import gov_guides  # noqa: E402

# The corpus's language tags in Argos codes: the agencies write Traditional Chinese.
ARGOS_CODE = {"zh": "zt", "en": "en"}
TARGETS = ("en", "vi", "zt")
PIVOT = "en"


def install(from_code: str, to_code: str) -> None:
    """Download and install the Argos model for one language pair unless it is installed."""
    installed = argostranslate.package.get_installed_packages()
    if any(p.from_code == from_code and p.to_code == to_code for p in installed):
        return
    argostranslate.package.update_package_index()
    package = next(
        p
        for p in argostranslate.package.get_available_packages()
        if p.from_code == from_code and p.to_code == to_code
    )
    argostranslate.package.install_from_path(package.download())


@cache
def available_pairs() -> set[tuple[str, str]]:
    argostranslate.package.update_package_index()
    return {
        (p.from_code, p.to_code)
        for p in argostranslate.package.get_available_packages()
    }


def lines(article: dict) -> list[str]:
    return [
        text
        for text in (article["title"], article["org"], *article["body"].split("\n"))
        if text.strip()
    ]


def main() -> None:
    out_dir = gov_guides.DATA_DIR / "translations"
    out_dir.mkdir(exist_ok=True)
    articles = [a for a in gov_guides.load_articles() if not a["is_service"]]
    pivot_cache: dict[str, str] = {}

    for target in TARGETS:
        path = out_dir / f"{target}.json"
        previous = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        result: dict[str, str] = {}
        for article in articles:
            source = ARGOS_CODE[article["lang"]]
            if source == target:
                continue
            todo = [t for t in lines(article) if t not in previous and t not in result]
            print(f"{target} {article['id']}: {len(todo)} new lines", flush=True)
            for text in lines(article):
                if text in previous:
                    result[text] = previous[text]
                    continue
                if text in result:
                    continue
                result[text] = translate(text, source, target, pivot_cache)
        path.write_text(
            json.dumps(dict(sorted(result.items())), ensure_ascii=False, indent=1)
            + "\n",
            encoding="utf-8",
        )
        print(f"wrote {path.name}: {len(result)} lines", flush=True)


def translate(text: str, source: str, target: str, pivot_cache: dict[str, str]) -> str:
    """Translate one line, through English when Argos has no direct model for the pair."""
    if (source, target) in available_pairs():
        install(source, target)
        return argostranslate.translate.translate(text, source, target)
    install(source, PIVOT)
    install(PIVOT, target)
    if text not in pivot_cache:
        pivot_cache[text] = argostranslate.translate.translate(text, source, PIVOT)
    return argostranslate.translate.translate(pivot_cache[text], PIVOT, target)


if __name__ == "__main__":
    main()
