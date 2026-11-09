"""Fetch and parse one course's syllabus from the NCU course detail page."""

import copy
import logging
from typing import Any

import bs4
import httpx

logger = logging.getLogger(__name__)

DETAIL_URL_TEMPLATE = (
    "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs={serial_no}"
)
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "zh-TW",
}

_FIELD_BY_TITLE = {
    "課程目標": "objectives",
    "授課內容": "content",
    "教科書/參考書": "books",
    "授課方式": "teaching_method",
    "評量配分比例": "grading_policy",
    "評量配分比重": "grading_policy",
}


def detail_url(serial_no: str) -> str:
    return DETAIL_URL_TEMPLATE.format(serial_no=serial_no)


def _text_with_newlines(element: bs4.Tag) -> str:
    el_copy = copy.copy(element)
    for br in el_copy.find_all("br"):
        br.replace_with("\n")

    lines: list[str] = []
    for line in el_copy.get_text().split("\n"):
        line = line.strip()
        if line == "" and lines and lines[-1] == "":
            continue
        lines.append(line)
    return "\n".join(lines).strip()


def parse_course_detail(html: bytes | str) -> dict[str, Any] | None:
    """Return the syllabus fields, or None when the page carries no syllabus."""
    soup = bs4.BeautifulSoup(html, "html.parser")
    data: dict[str, Any] = {
        "objectives": "",
        "content": "",
        "books": "",
        "teaching_method": "",
        "grading_policy": "",
        "distribution_conditions": [],
    }

    for row in soup.select("table.classBase tr"):
        tds = row.find_all("td", recursive=False)
        if len(tds) < 2 or "subTitle" not in (tds[0].get("class") or []):
            continue
        field = _FIELD_BY_TITLE.get(tds[0].get_text(strip=True))
        value = _text_with_newlines(tds[1])
        if field and value and value.lower() != "no data":
            data[field] = value

    header = soup.find(lambda tag: tag.name == "th" and "分發條件" in tag.get_text())
    next_row = header.parent.find_next_sibling("tr") if header else None
    conditions_table = next_row.find("table") if next_row else None
    if conditions_table:
        for row in conditions_table.find_all("tr")[1:]:
            tds = row.find_all("td")
            priority = tds[0].get_text(strip=True) if len(tds) >= 2 else ""
            # Clients type priority as an integer; a non-numeric one is dropped.
            if priority.isdigit():
                data["distribution_conditions"].append(
                    {"priority": int(priority), "rule": tds[1].get_text(strip=True)}
                )

    return data if any(data.values()) else None


def fetch_course_detail(serial_no: str, timeout: float = 12.0) -> dict[str, Any] | None:
    """Fetch the live detail page. Raises httpx.HTTPError when NCU is unreachable."""
    response = httpx.get(
        detail_url(serial_no), headers=HEADERS, timeout=timeout, follow_redirects=True
    )
    response.raise_for_status()
    return parse_course_detail(response.content)
