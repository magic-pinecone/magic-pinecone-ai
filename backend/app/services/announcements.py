"""Parsers and source catalog for NCU Affiliation and Department announcements."""

import hashlib
import re
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlencode, urljoin

import bs4

from app.models.announcement import AffiliationType, AnnouncementScope

SCHOLARSHIP_URL = "https://cis.ncu.edu.tw/Scholarship/"

_DATE_RE = re.compile(r"\b(\d{3,4})[./-](\d{1,2})[./-](\d{1,2})\b")


@dataclass(frozen=True)
class AnnouncementSourceConfig:
    source_id: str
    scope: AnnouncementScope
    unit_name: str
    category: str
    url: str
    affiliations: list[AffiliationType] = field(default_factory=lambda: ["ALL"])
    college_name: str = ""
    department_name: str = ""
    department_id: str | None = None


AFFILIATION_SOURCES: tuple[AnnouncementSourceConfig, ...] = (
    AnnouncementSourceConfig(
        source_id="oia",
        scope="AFFILIATION",
        unit_name="國際事務處",
        category="國際事務",
        url="https://oia.ncu.edu.tw/p/403-1026-342.php?Lang=zh-tw",
        affiliations=["INTERNATIONAL", "UNDERGRADUATE", "GRADUATE"],
    ),
    AnnouncementSourceConfig(
        source_id="oia_intl_students",
        scope="AFFILIATION",
        unit_name="國際事務處境外生事務",
        category="國際事務",
        url="https://oia.ncu.edu.tw/p/403-1026-571.php?Lang=zh-tw",
        affiliations=["INTERNATIONAL"],
    ),
    AnnouncementSourceConfig(
        source_id="oia_external",
        scope="AFFILIATION",
        unit_name="國際事務處校外資源",
        category="國際事務",
        url="https://oia.ncu.edu.tw/p/403-1026-569.php?Lang=zh-tw",
        affiliations=["INTERNATIONAL", "UNDERGRADUATE", "GRADUATE"],
    ),
    AnnouncementSourceConfig(
        source_id="oaa",
        scope="AFFILIATION",
        unit_name="教務處",
        category="教務",
        url="https://pdc.adm.ncu.edu.tw/",
        affiliations=["UNDERGRADUATE", "GRADUATE"],
    ),
    AnnouncementSourceConfig(
        source_id="osa",
        scope="AFFILIATION",
        unit_name="學務處",
        category="學務",
        url="https://osa.ncu.edu.tw/",
        affiliations=["UNDERGRADUATE", "GRADUATE"],
    ),
    AnnouncementSourceConfig(
        source_id="cc",
        scope="AFFILIATION",
        unit_name="電子計算機中心",
        category="資訊服務",
        url="https://www.cc.ncu.edu.tw/",
        affiliations=["ALL"],
    ),
    AnnouncementSourceConfig(
        source_id="hr",
        scope="AFFILIATION",
        unit_name="人事室",
        category="行政公告",
        url="https://ncu7060.ncu.edu.tw/",
        affiliations=["FACULTY"],
    ),
)

DEPARTMENT_SOURCES: tuple[AnnouncementSourceConfig, ...] = (
    # 資電學院
    AnnouncementSourceConfig(
        source_id="dept_csie",
        scope="DEPARTMENT",
        unit_name="資訊工程學系",
        category="系所公告",
        url="https://www.csie.ncu.edu.tw/p/403-1013-518.php?Lang=zh-tw",
        college_name="資訊電機學院",
        department_name="資訊工程學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_ee",
        scope="DEPARTMENT",
        unit_name="電機工程學系",
        category="系所公告",
        url="https://www.ee.ncu.edu.tw/",
        college_name="資訊電機學院",
        department_name="電機工程學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_ce",
        scope="DEPARTMENT",
        unit_name="通訊工程學系",
        category="系所公告",
        url="https://www.ce.ncu.edu.tw/newslist/all",
        college_name="資訊電機學院",
        department_name="通訊工程學系",
    ),
    # 理學院
    AnnouncementSourceConfig(
        source_id="dept_math",
        scope="DEPARTMENT",
        unit_name="數學系",
        category="系所公告",
        url="https://www.math.ncu.edu.tw/",
        college_name="理學院",
        department_name="數學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_phy",
        scope="DEPARTMENT",
        unit_name="物理學系",
        category="系所公告",
        url="https://www.phy.ncu.edu.tw/公告",
        college_name="理學院",
        department_name="物理學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_chem",
        scope="DEPARTMENT",
        unit_name="化學學系",
        category="系所公告",
        url="https://www.chem.ncu.edu.tw/zh_tw/news/news101",
        college_name="理學院",
        department_name="化學學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_dop",
        scope="DEPARTMENT",
        unit_name="光電科學與工程學系",
        category="系所公告",
        url="https://www.dop.ncu.edu.tw/",
        college_name="理學院",
        department_name="光電科學與工程學系",
    ),
    # 工學院
    AnnouncementSourceConfig(
        source_id="dept_me",
        scope="DEPARTMENT",
        unit_name="機械工程學系",
        category="系所公告",
        url="https://www.me.ncu.edu.tw/",
        college_name="工學院",
        department_name="機械工程學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_cme",
        scope="DEPARTMENT",
        unit_name="化學工程與材料工程學系",
        category="系所公告",
        url="https://www.cme.ncu.edu.tw/",
        college_name="工學院",
        department_name="化學工程與材料工程學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_cv",
        scope="DEPARTMENT",
        unit_name="土木工程學系",
        category="系所公告",
        url="https://www.cv.ncu.edu.tw/category/%e7%b3%bb%e8%be%a6%e5%85%ac%e5%91%8a/",
        college_name="工學院",
        department_name="土木工程學系",
    ),
    # 管理學院
    AnnouncementSourceConfig(
        source_id="dept_ba",
        scope="DEPARTMENT",
        unit_name="企業管理學系",
        category="系所公告",
        url="https://ba.mgt.ncu.edu.tw/?newsid=0",
        college_name="管理學院",
        department_name="企業管理學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_im",
        scope="DEPARTMENT",
        unit_name="資訊管理學系",
        category="系所公告",
        url="https://im.mgt.ncu.edu.tw/",
        college_name="管理學院",
        department_name="資訊管理學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_fm",
        scope="DEPARTMENT",
        unit_name="財務金融學系",
        category="系所公告",
        url="https://fm.mgt.ncu.edu.tw/zh-TW/category-list/news",
        college_name="管理學院",
        department_name="財務金融學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_ec",
        scope="DEPARTMENT",
        unit_name="經濟學系",
        category="系所公告",
        url="https://ec.mgt.ncu.edu.tw/",
        college_name="管理學院",
        department_name="經濟學系",
    ),
    # 文學院
    AnnouncementSourceConfig(
        source_id="dept_chinese",
        scope="DEPARTMENT",
        unit_name="中國文學系",
        category="系所公告",
        url="https://chinese.ncu.edu.tw/",
        college_name="文學院",
        department_name="中國文學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_english",
        scope="DEPARTMENT",
        unit_name="英美語文學系",
        category="系所公告",
        url="https://english.ncu.edu.tw/",
        college_name="文學院",
        department_name="英美語文學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_french",
        scope="DEPARTMENT",
        unit_name="法國語文學系",
        category="系所公告",
        url="https://french.ncu.edu.tw/",
        college_name="文學院",
        department_name="法國語文學系",
    ),
    # 地球科學學院
    AnnouncementSourceConfig(
        source_id="dept_atm",
        scope="DEPARTMENT",
        unit_name="大氣科學學系",
        category="系所公告",
        url="https://www.atm.ncu.edu.tw/news.php?category=1",
        college_name="地球科學學院",
        department_name="大氣科學學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_earth",
        scope="DEPARTMENT",
        unit_name="地球科學學系",
        category="系所公告",
        url="https://www.earth.ncu.edu.tw/",
        college_name="地球科學學院",
        department_name="地球科學學系",
    ),
    # 客家學院 & 生醫理工學院
    AnnouncementSourceConfig(
        source_id="dept_hakka",
        scope="DEPARTMENT",
        unit_name="客家語文暨社會科學學系",
        category="系所公告",
        url="https://hakka.ncu.edu.tw/",
        college_name="客家學院",
        department_name="客家語文暨社會科學學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_ls",
        scope="DEPARTMENT",
        unit_name="生命科學系",
        category="系所公告",
        url="https://ls.ncu.edu.tw/",
        college_name="生醫理工學院",
        department_name="生命科學系",
    ),
    AnnouncementSourceConfig(
        source_id="dept_bse",
        scope="DEPARTMENT",
        unit_name="生醫科學與工程學系",
        category="系所公告",
        url="https://bse.ncu.edu.tw/",
        college_name="生醫理工學院",
        department_name="生醫科學與工程學系",
    ),
)


def make_announcement_id(source_id: str, key: str) -> str:
    """Create a deterministic primary key from source_id and an item key."""
    digest = hashlib.sha256(f"{source_id}:{key}".encode()).hexdigest()[:16]
    return f"{source_id}:{digest}"


def normalize_date_str(raw: str | None) -> str | None:
    """Extract and normalize a date string (Gregorian or ROC) to YYYY-MM-DD."""
    if not raw:
        return None
    match = _DATE_RE.search(raw)
    if not match:
        return None
    year_str, month_str, day_str = match.groups()
    year = int(year_str)
    if year < 1911:
        year += 1911
    month = int(month_str)
    day = int(day_str)
    if not (1 <= month <= 12 and 1 <= day <= 31):
        return None
    return f"{year:04d}-{month:02d}-{day:02d}"


def infer_affiliations(
    text: str, default: list[AffiliationType] | None = None
) -> list[str]:
    """Infer target student/faculty affiliations from announcement text."""
    matched: list[str] = []
    if any(k in text for k in ("國際生", "外籍生", "僑生", "陸生", "境外生", "交換生")):
        matched.append("INTERNATIONAL")
    if any(k in text for k in ("學士", "大學部", "大一", "大二", "大三", "大四")):
        matched.append("UNDERGRADUATE")
    if any(k in text for k in ("碩士", "博士", "研究所", "碩博", "研究生")):
        matched.append("GRADUATE")
    if any(k in text for k in ("教職", "專任教師", "兼任教師", "職員", "約用人員")):
        matched.append("FACULTY")

    if matched:
        return matched
    if default:
        return list(default)
    return ["ALL"]


def _parse_summary_lines(text: str) -> dict[str, str]:
    """Parse colon-separated key-value summary lines from NCU scholarship rows."""
    summary: dict[str, str] = {}
    cleaned = text.replace("\n下載", " 下載")
    for line in cleaned.split("\n"):
        line = line.strip()
        if not line:
            continue
        if " :" in line:
            parts = line.split(" :", 1)
        elif "：" in line:
            parts = line.split("：", 1)
        elif ":" in line:
            parts = line.split(":", 1)
        else:
            parts = ["備註", line]

        label = parts[0].strip() or "備註"
        value = parts[1].strip()
        if value:
            summary[label] = value
    return summary


def parse_scholarship_announcements(html: bytes | str) -> list[dict[str, Any]]:
    """Parse scholarship and part-time job announcements from NCU Scholarship system."""
    soup = bs4.BeautifulSoup(html, "html.parser")
    table = soup.find("table", class_="news_list")
    if not table:
        return []

    results: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    for row in table.find_all("tr")[1:]:
        cols = row.find_all("td")
        if len(cols) < 4:
            continue

        raw_category = cols[1].get_text(strip=True)
        title = cols[2].get_text(strip=True)
        if not title:
            continue

        category = raw_category or "獎學金"
        raw_summary_text = cols[3].get_text(separator="\n", strip=True)
        summary = _parse_summary_lines(raw_summary_text)

        attachment_url: str | None = None
        link_tag = cols[3].find("a")
        if link_tag and link_tag.get("href"):
            href = str(link_tag.get("href")).strip()
            if href.startswith(".."):
                attachment_url = href.replace("..", "https://cis.ncu.edu.tw", 1)
            elif href.startswith("/"):
                attachment_url = f"https://cis.ncu.edu.tw{href}"
            else:
                attachment_url = urljoin(SCHOLARSHIP_URL, href)

        choose_type = "parttime" if category == "招募資訊" else "scholar"
        query_str = urlencode({"searchJob": title, "ChooseType": choose_type})
        canonical_url = f"https://cis.ncu.edu.tw/Scholarship?{query_str}"

        published_date = normalize_date_str(
            summary.get("開始日期") or summary.get("結束日期")
        )
        qualification_text = f"{title} {summary.get('申請資格', '')}"
        affiliations = infer_affiliations(
            qualification_text, default=["UNDERGRADUATE", "GRADUATE"]
        )

        item_key = f"{category}:{title}:{summary.get('開始日期', '')}"
        announcement_id = make_announcement_id("scholarship", item_key)
        if announcement_id in seen_ids:
            continue
        seen_ids.add(announcement_id)

        results.append(
            {
                "announcement_id": announcement_id,
                "scope": "AFFILIATION",
                "source_id": "scholarship",
                "category": category,
                "unit_name": "獎助學金暨工讀系統",
                "college_id": None,
                "college_name": "",
                "department_id": None,
                "department_name": "",
                "affiliations": affiliations,
                "title": title,
                "summary": summary,
                "published_date": published_date,
                "url": canonical_url,
                "attachment_url": attachment_url,
                "is_pinned": False,
            }
        )

    return results


def parse_rpage_announcements(
    html: bytes | str, source: AnnouncementSourceConfig
) -> list[dict[str, Any]]:
    """Parse announcement items from an NCU RPage (p/403-...) list page."""
    soup = bs4.BeautifulSoup(html, "html.parser")
    results: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    # RPage renders announcement rows either as `tr` inside a table or `.d-item` / `.listBS` blocks
    containers: list[bs4.Tag] = []
    for selector in (
        "table tbody tr",
        "div.d-item",
        "div.row.listBS",
        "li.d-item",
        "article",
        "div.news-item",
    ):
        found = soup.select(selector)
        if found:
            containers.extend(found)

    # Fallback: locate `.mtitle` or news heading wrappers directly if custom markup is used
    if not containers:
        for mtitle in soup.select(".mtitle, h2, h3, h4"):
            parent = mtitle.find_parent(["tr", "li", "div", "article"])
            if parent and parent not in containers:
                containers.append(parent)

    for item in containers:
        anchor = item.select_one(".mtitle a[href]") or item.find(
            "a",
            href=re.compile(r"/p/40[456]-|/news|news_\d+\.php|/article/|/post\b|http"),
        )
        if not anchor:
            continue

        raw_href = str(anchor.get("href") or "").strip()
        if not raw_href or raw_href.startswith("javascript:"):
            continue

        title = anchor.get_text(" ", strip=True)
        if not title:
            continue

        full_url = urljoin(source.url, raw_href)

        date_tag = item.select_one(".mdate") or item.find(
            attrs={"data-th": re.compile(r"日期|時間|Date", re.I)}
        )
        raw_date = (
            date_tag.get_text(" ", strip=True)
            if date_tag
            else item.get_text(" ", strip=True)
        )
        published_date = normalize_date_str(raw_date)

        is_pinned = bool(
            item.select_one(".PtTop, .top, img[alt*='置頂'], i[title*='置頂']")
            or "置頂" in item.get_text(" ", strip=True)[:10]
        )

        affiliations = (
            infer_affiliations(title, default=source.affiliations)
            if source.scope == "AFFILIATION"
            else list(source.affiliations)
        )

        announcement_id = make_announcement_id(source.source_id, full_url)
        if announcement_id in seen_ids:
            continue
        seen_ids.add(announcement_id)

        summary: dict[str, str] = {"發布單位": source.unit_name}
        if published_date:
            summary["發布日期"] = published_date

        results.append(
            {
                "announcement_id": announcement_id,
                "scope": source.scope,
                "source_id": source.source_id,
                "category": source.category,
                "unit_name": source.unit_name,
                "college_id": None,
                "college_name": source.college_name,
                "department_id": source.department_id,
                "department_name": source.department_name,
                "affiliations": affiliations,
                "title": title,
                "summary": summary,
                "published_date": published_date,
                "url": full_url,
                "attachment_url": None,
                "is_pinned": is_pinned,
            }
        )

    return results
