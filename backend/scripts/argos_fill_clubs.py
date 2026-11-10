"""Fill the club translation cache for one language with Argos Translate, offline.

Argos is open-source machine translation that runs locally, so it needs no API and has no rate limit. It is too
heavy (torch, spaCy) for the API image, so it runs here once and writes the same `translations` rows that
GET /api/v1/clubs?lang= reads. Texts that already have a translation are left alone.

Usage, from backend/ with the compose database up and backend/.env copied from .env.example:

    uv run --with argostranslate python -I scripts/argos_fill_clubs.py vi

Argos has no Chinese-to-Vietnamese model, so it pivots through English.
"""

import os
import sys
from pathlib import Path

# -I leaves the script's own directory off sys.path, so add backend/ for the app package.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
# Argos's default sentence splitter is stanza's torch model, which ran out of memory on a
# 16 GB machine running Docker; MiniSBD is a small ONNX model. Read at import time.
os.environ.setdefault("ARGOS_CHUNK_TYPE", "MINISBD")

import argostranslate.package  # noqa: E402
import argostranslate.translate  # noqa: E402
from sqlmodel import Session, select  # noqa: E402

from app.api.routes.clubs import TRANSLATED  # noqa: E402
from app.core.db import engine  # noqa: E402
from app.models.club import Club  # noqa: E402
from app.models.translation import Translation  # noqa: E402
from app.services.translator import _hash  # noqa: E402
from app.tasks.club_scraper import CLUB_CATEGORIES  # noqa: E402

SOURCE = "zh"
PIVOT = "en"


def install(from_code: str, to_code: str) -> None:
    """Download and install the Argos model for one language pair unless it is installed."""
    installed = argostranslate.package.get_installed_packages()
    if any(p.from_code == from_code and p.to_code == to_code for p in installed):
        return
    argostranslate.package.update_package_index()
    package = next(
        (
            p
            for p in argostranslate.package.get_available_packages()
            if p.from_code == from_code and p.to_code == to_code
        ),
        None,
    )
    if package is None:
        sys.exit(f"Argos has no {from_code}->{to_code} model")
    argostranslate.package.install_from_path(package.download())


def translator(lang: str):
    """A function translating Chinese into `lang`, pivoting through English when no direct model exists."""
    install(SOURCE, PIVOT)
    if lang == PIVOT:
        return lambda text: argostranslate.translate.translate(text, SOURCE, PIVOT)
    install(PIVOT, lang)
    return lambda text: argostranslate.translate.translate(
        argostranslate.translate.translate(text, SOURCE, PIVOT), PIVOT, lang
    )


def main(lang: str) -> None:
    with Session(engine) as session:
        clubs = session.exec(select(Club)).all()
        texts = {
            getattr(c, f) for c in clubs for f in TRANSLATED if f != "category_name"
        }
        texts |= set(CLUB_CATEGORIES.values())
        texts = {t for t in texts if t.strip()}
        cached = set(
            session.exec(
                select(Translation.source_hash).where(Translation.lang == lang)
            ).all()
        )
        missing = sorted(t for t in texts if _hash(t) not in cached)
        print(f"{len(texts)} club texts, {len(missing)} without a {lang} translation")
        if not missing:
            return

        translate = translator(lang)
        for i, text in enumerate(missing, 1):
            # Each row commits on its own, so an interrupted run keeps its progress.
            session.add(
                Translation(lang=lang, source_hash=_hash(text), text=translate(text))
            )
            session.commit()
            if i % 25 == 0 or i == len(missing):
                print(f"{i}/{len(missing)}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
