"""Async scraper and Celery task for NCU Affiliation and Department announcements."""

import asyncio
import logging
import re
from datetime import UTC, datetime
from typing import Any

import httpx
from sqlmodel import Session, select
from tenacity import retry, stop_after_attempt, wait_exponential

from app.core.celery_app import celery_app
from app.core.db import engine
from app.models.announcement import (
    ANNOUNCEMENT_STATE_ID,
    Announcement,
    AnnouncementSyncState,
)
from app.models.course import College, Department
from app.services.announcements import (
    AFFILIATION_SOURCES,
    DEPARTMENT_SOURCES,
    SCHOLARSHIP_URL,
    AnnouncementSourceConfig,
    parse_rpage_announcements,
    parse_scholarship_announcements,
)

logger = logging.getLogger(__name__)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7",
}

_DEPT_ALIASES: dict[str, tuple[str, ...]] = {
    "資訊工程學系": ("資工系", "資訊工程系", "資工"),
    "電機工程學系": ("電機系", "電機"),
    "通訊工程學系": ("通訊系", "通訊"),
    "數學系": ("數學",),
    "物理學系": ("物理系", "物理"),
    "化學學系": ("化學系", "化學"),
    "光電科學與工程學系": ("光電系", "光電"),
    "機械工程學系": ("機械系", "機械"),
    "化學工程與材料工程學系": ("化材系", "化材"),
    "土木工程學系": ("土木系", "土木"),
    "企業管理學系": ("企管系", "企管"),
    "資訊管理學系": ("資管系", "資管"),
    "財務金融學系": ("財金系", "財金"),
    "經濟學系": ("經濟系", "經濟"),
    "中國文學系": ("中文系", "中文"),
    "英美語文學系": ("英文系", "英美系", "英文"),
    "法國語文學系": ("法文系", "法文"),
    "大氣科學學系": ("大氣系", "大氣"),
    "地球科學學系": ("地科系", "地科"),
    "客家語文暨社會科學學系": ("客家系", "客家"),
    "生命科學系": ("生科系", "生科"),
    "生醫科學與工程學系": ("生醫系", "生醫"),
}


def canonical_department_names(name: str) -> set[str]:
    """Return a set of equivalent department names (full and abbreviated)."""
    cleaned = name.strip()
    if not cleaned:
        return set()
    names = {cleaned}
    for full_name, aliases in _DEPT_ALIASES.items():
        if cleaned == full_name or cleaned in aliases:
            names.add(full_name)
            names.update(aliases)
    return names


def match_department(
    department_name: str, departments: list[Department]
) -> Department | None:
    """Match a department name (full or abbreviated) to a Department row."""
    if not department_name:
        return None
    candidates = canonical_department_names(department_name)
    for dept in departments:
        if dept.department_name in candidates:
            return dept
    for dept in departments:
        if any(
            c in dept.department_name or dept.department_name in c for c in candidates
        ):
            return dept
    return None


class NCUAnnouncementFetcher:
    """Async engine for fetching NCU affiliation and department announcements."""

    def __init__(self, concurrency_limit: int = 8):
        self.semaphore = asyncio.Semaphore(concurrency_limit)

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=1, max=8),
        reraise=True,
    )
    async def _get(self, client: httpx.AsyncClient, url: str) -> httpx.Response:
        resp = await client.get(
            url, headers=HEADERS, follow_redirects=True, timeout=15.0
        )
        resp.raise_for_status()
        return resp

    async def fetch_scholarship_source(
        self, client: httpx.AsyncClient
    ) -> tuple[str, list[dict[str, Any]], bool]:
        """Fetch NCU scholarship & part-time job announcements."""
        async with self.semaphore:
            try:
                resp = await self._get(client, SCHOLARSHIP_URL)
                items = parse_scholarship_announcements(resp.content)
                return ("scholarship", items, True)
            except Exception as exc:
                logger.warning(f"Failed to fetch scholarship announcements: {exc}")
                return ("scholarship", [], False)

    async def fetch_rpage_source(
        self, client: httpx.AsyncClient, source: AnnouncementSourceConfig
    ) -> tuple[str, list[dict[str, Any]], bool]:
        """Fetch announcements from one RPage source."""
        async with self.semaphore:
            try:
                resp = await self._get(client, source.url)
                items = parse_rpage_announcements(resp.content, source)
                return (source.source_id, items, True)
            except Exception as exc:
                logger.warning(
                    f"Failed to fetch RPage source {source.source_id} ({source.url}): {exc}"
                )
                return (source.source_id, [], False)

    async def fetch_all_data(self) -> dict[str, Any]:
        """Concurrently fetch all affiliation and department announcement sources."""
        async with httpx.AsyncClient(verify=False) as client:
            tasks = [self.fetch_scholarship_source(client)]
            for src in (*AFFILIATION_SOURCES, *DEPARTMENT_SOURCES):
                tasks.append(self.fetch_rpage_source(client, src))

            results = await asyncio.gather(*tasks)

        all_items: list[dict[str, Any]] = []
        succeeded_sources: set[str] = set()
        for source_id, items, ok in results:
            if ok:
                succeeded_sources.add(source_id)
                all_items.extend(items)

        return {
            "announcements": all_items,
            "succeeded_sources": list(succeeded_sources),
        }


def sync_announcements_to_db(session: Session, data: dict[str, Any]) -> int:
    """Upsert scraped announcements and prune stale entries for succeeded sources."""
    now = datetime.now(UTC)
    items: list[dict[str, Any]] = data.get("announcements", [])
    succeeded_sources: set[str] = set(data.get("succeeded_sources", []))

    departments = list(session.exec(select(Department)).all())
    colleges = {
        c.college_id: re.sub(r"\(.*?\)", "", c.college_name).strip()
        for c in session.exec(select(College)).all()
    }

    scraped_ids_by_source: dict[str, set[str]] = {s: set() for s in succeeded_sources}

    for item in items:
        source_id = item["source_id"]
        announcement_id = item["announcement_id"]
        scraped_ids_by_source.setdefault(source_id, set()).add(announcement_id)

        dept = match_department(item.get("department_name", ""), departments)
        department_id = item.get("department_id") or (
            dept.department_id if dept else None
        )
        college_id = item.get("college_id") or (dept.college_id if dept else None)
        college_name = item.get("college_name") or (
            colleges.get(college_id, "") if college_id else ""
        )

        existing = session.get(Announcement, announcement_id)
        if existing:
            existing.scope = item["scope"]
            existing.source_id = source_id
            existing.category = item["category"]
            existing.unit_name = item["unit_name"]
            existing.college_id = college_id
            existing.college_name = college_name
            existing.department_id = department_id
            existing.department_name = item.get("department_name", "")
            existing.affiliations = item.get("affiliations", ["ALL"])
            existing.title = item["title"]
            existing.summary = item.get("summary", {})
            existing.published_date = item.get("published_date")
            existing.url = item["url"]
            existing.attachment_url = item.get("attachment_url")
            existing.is_pinned = item.get("is_pinned", False)
            existing.updated_at = now
        else:
            session.add(
                Announcement(
                    announcement_id=announcement_id,
                    scope=item["scope"],
                    source_id=source_id,
                    category=item["category"],
                    unit_name=item["unit_name"],
                    college_id=college_id,
                    college_name=college_name,
                    department_id=department_id,
                    department_name=item.get("department_name", ""),
                    affiliations=item.get("affiliations", ["ALL"]),
                    title=item["title"],
                    summary=item.get("summary", {}),
                    published_date=item.get("published_date"),
                    url=item["url"],
                    attachment_url=item.get("attachment_url"),
                    is_pinned=item.get("is_pinned", False),
                    updated_at=now,
                )
            )

    # Remove stale announcements from sources that succeeded and returned items
    for source_id, current_ids in scraped_ids_by_source.items():
        if not current_ids:
            continue
        existing_for_source = session.exec(
            select(Announcement).where(Announcement.source_id == source_id)
        ).all()
        for row in existing_for_source:
            if row.announcement_id not in current_ids:
                session.delete(row)

    state = session.get(AnnouncementSyncState, ANNOUNCEMENT_STATE_ID)
    if state:
        state.last_synced_at = now
    else:
        session.add(AnnouncementSyncState(id=ANNOUNCEMENT_STATE_ID, last_synced_at=now))

    session.commit()
    return len(items)


def _update_task_progress(task: Any, state: str, meta: dict):
    if hasattr(task, "request") and task.request and task.request.id:
        try:
            task.update_state(state=state, meta=meta)
        except Exception:
            pass


@celery_app.task(bind=True, name="tasks.announcement_scraper.scrape_ncu_announcements")
def scrape_ncu_announcements(self: Any, save_to_db: bool = True) -> dict[str, Any]:
    """Celery task to scrape NCU affiliation and department announcements."""
    logger.info("Starting NCU announcement scraping Celery worker task...")
    _update_task_progress(
        self,
        state="PROGRESS",
        meta={"status": "Fetching live announcements from NCU..."},
    )

    fetcher = NCUAnnouncementFetcher(concurrency_limit=8)
    data = asyncio.run(fetcher.fetch_all_data())
    announcements = data["announcements"]
    succeeded_sources = data["succeeded_sources"]

    logger.info(
        f"Fetched {len(announcements)} announcements across {len(succeeded_sources)} sources."
    )

    if save_to_db:
        _update_task_progress(
            self, state="PROGRESS", meta={"status": "Updating database..."}
        )
        with Session(engine) as session:
            sync_announcements_to_db(session, data)

    return {
        "status": "SUCCESS",
        "announcement_count": len(announcements),
        "succeeded_source_count": len(succeeded_sources),
    }
