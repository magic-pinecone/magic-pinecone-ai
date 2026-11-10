"""Translate text with Google Translate's free web endpoint, caching every result in the database."""

import hashlib
import logging
from collections.abc import Iterable
from concurrent.futures import ThreadPoolExecutor

import httpx
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select

from app.models.translation import Translation

logger = logging.getLogger(__name__)

# shortcut: unofficial keyless endpoint with no SLA or published rate limit; move to the paid Cloud Translation API if it starts refusing.
ENDPOINT = "https://translate.googleapis.com/translate_a/single"
WORKERS = 8


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def _fetch(client: httpx.Client, text: str, lang: str) -> str | None:
    """Translate one text, or None when the endpoint fails."""
    try:
        response = client.post(
            ENDPOINT,
            params={"client": "gtx", "sl": "auto", "tl": lang, "dt": "t"},
            data={"q": text},
        )
        response.raise_for_status()
        # Body is [[[translated, source, ...], ...], ...], one row per sentence.
        return "".join(row[0] for row in response.json()[0] if row[0])
    except (httpx.HTTPError, ValueError, LookupError, TypeError) as e:
        logger.warning("Translating to %s failed: %s", lang, e)
        return None


def translate(session: Session, texts: Iterable[str], lang: str) -> dict[str, str]:
    """Map each non-blank text to its translation into `lang`; texts that failed to translate are left out."""
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
        with httpx.Client(timeout=10) as client, ThreadPoolExecutor(WORKERS) as pool:
            fetched = list(pool.map(lambda t: _fetch(client, t, lang), missing))
        new = {t: tr for t, tr in zip(missing, fetched, strict=True) if tr is not None}
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
