"""Translate text with the self-hosted LibreTranslate service, caching every result in the database.

LibreTranslate runs Argos Translate's open models in its own container next to the API (`translate` in
compose.yaml), so text never leaves the server and there is no rate limit. Its models cover English,
Simplified (zh) and Traditional (zt) Chinese and Vietnamese; Chinese and Vietnamese pivot through English.
"""

import hashlib
import logging
from collections.abc import Iterable
from concurrent.futures import ThreadPoolExecutor

import httpx
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select

from app.core.config import settings
from app.models.translation import Translation

logger = logging.getLogger(__name__)

# Lines per request; LibreTranslate translates a list in one call.
BATCH = 40
# Matches LibreTranslate's default worker threads, so batches do not queue behind each other.
WORKERS = 4
# CPU translation of a long batch, pivoting through English, can take tens of seconds.
TIMEOUT_SECONDS = 180


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def engine_code(lang: str) -> str:
    """The LibreTranslate code for a language tag: zt for Traditional Chinese, else the base language."""
    tag = lang.lower()
    if tag.startswith("zh"):
        simplified = tag in {"zh-cn", "zh-sg", "zh-hans"} or tag.startswith("zh-hans-")
        return "zh" if simplified else "zt"
    return tag.split("-")[0]


def _fetch(
    client: httpx.Client, texts: list[str], source: str, lang: str
) -> list[str] | None:
    """Translate one batch, or None when the service is down or refuses the pair."""
    try:
        response = client.post(
            f"{settings.TRANSLATOR_URL}/translate",
            json={
                "q": texts,
                "source": engine_code(source) if source != "auto" else "auto",
                "target": engine_code(lang),
                "format": "text",
            },
        )
        response.raise_for_status()
        translated = response.json()["translatedText"]
        return translated if len(translated) == len(texts) else None
    except (httpx.HTTPError, ValueError, LookupError, TypeError) as e:
        logger.warning("Translating %s to %s failed: %s", source, lang, e)
        return None


def translate(
    session: Session, texts: Iterable[str], lang: str, source: str = "auto"
) -> dict[str, str]:
    """Map each non-blank text to its translation into `lang`; texts that failed to translate are left out.

    `source` is the language the texts are written in; "auto" lets the service guess, which is unreliable
    for short lines.
    """
    by_hash = {_hash(t): t for t in texts if t.strip()}
    cached = session.exec(
        select(Translation).where(
            Translation.lang == lang,
            Translation.source_hash.in_(by_hash),  # type: ignore[attr-defined]
        )
    ).all()
    result = {by_hash[row.source_hash]: row.text for row in cached}

    missing = [t for t in by_hash.values() if t not in result]
    if missing:
        batches = [missing[i : i + BATCH] for i in range(0, len(missing), BATCH)]
        with (
            httpx.Client(timeout=TIMEOUT_SECONDS) as client,
            ThreadPoolExecutor(WORKERS) as pool,
        ):
            fetched = list(pool.map(lambda b: _fetch(client, b, source, lang), batches))
        new = {
            text: translation
            for batch, translations in zip(batches, fetched, strict=True)
            if translations is not None
            for text, translation in zip(batch, translations, strict=True)
        }
        session.add_all(
            Translation(lang=lang, source_hash=_hash(t), text=tr)
            for t, tr in new.items()
        )
        try:
            session.commit()
        except IntegrityError:
            session.rollback()  # A concurrent request cached the same text first.
        result.update(new)
    return result
