from datetime import UTC, datetime

import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app.api.deps import get_db
from app.main import app
from app.models.announcement import Announcement, AnnouncementSyncState
from app.models.course import College, Department
from app.services.announcements import (
    AnnouncementSourceConfig,
    normalize_date_str,
    parse_rpage_announcements,
    parse_scholarship_announcements,
)
from app.tasks.announcement_scraper import sync_announcements_to_db

SYNCED_AT = datetime(2026, 11, 9, 12, 0, tzinfo=UTC)


@pytest.fixture(name="session")
def session_fixture():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    with Session(engine) as session:
        yield session


@pytest.fixture(name="client")
def client_fixture(session: Session):
    app.dependency_overrides[get_db] = lambda: session
    yield TestClient(app)
    app.dependency_overrides.clear()


@pytest.fixture(name="synced_announcements")
def synced_announcements_fixture(session: Session):
    session.add(College(college_id="collegeI4", college_name="資訊電機學院(310)"))
    session.add(College(college_id="collegeI0", college_name="文學院(120)"))
    session.add(
        Department(
            department_id="dept4001",
            department_name="資工系",
            college_id="collegeI4",
        )
    )
    session.add(
        Department(
            department_id="dept1001",
            department_name="中文系",
            college_id="collegeI0",
        )
    )
    session.commit()

    scraped_data = {
        "succeeded_sources": ["scholarship", "oaa", "dept_csie", "dept_chinese"],
        "announcements": [
            {
                "announcement_id": "scholarship:001",
                "scope": "AFFILIATION",
                "source_id": "scholarship",
                "category": "獎學金",
                "unit_name": "獎助學金暨工讀系統",
                "college_id": None,
                "college_name": "",
                "department_id": None,
                "department_name": "",
                "affiliations": ["UNDERGRADUATE"],
                "title": "優秀大學部學生獎學金",
                "summary": {
                    "開始日期": "2026-10-01",
                    "結束日期": "2026-11-30",
                    "申請資格": "限學士班在學學生",
                },
                "published_date": "2026-10-01",
                "url": "https://cis.ncu.edu.tw/Scholarship?searchJob=%E5%84%AA%E7%A7%80&ChooseType=scholar",
                "attachment_url": "https://cis.ncu.edu.tw/Scholarship/files/rule.pdf",
                "is_pinned": False,
            },
            {
                "announcement_id": "oaa:002",
                "scope": "AFFILIATION",
                "source_id": "oaa",
                "category": "教務",
                "unit_name": "教務處",
                "college_id": None,
                "college_name": "",
                "department_id": None,
                "department_name": "",
                "affiliations": ["ALL"],
                "title": "115學年度第1學期期中停修公告",
                "summary": {"發布單位": "教務處", "發布日期": "2026-11-05"},
                "published_date": "2026-11-05",
                "url": "https://pdc.adm.ncu.edu.tw/p/404-1000-12345.php",
                "attachment_url": None,
                "is_pinned": True,
            },
            {
                "announcement_id": "dept_csie:001",
                "scope": "DEPARTMENT",
                "source_id": "dept_csie",
                "category": "系所公告",
                "unit_name": "資訊工程學系",
                "college_id": None,
                "college_name": "資訊電機學院",
                "department_id": None,
                "department_name": "資訊工程學系",
                "affiliations": ["ALL"],
                "title": "資工系專題發表會時程公告",
                "summary": {"發布單位": "資訊工程學系", "發布日期": "2026-11-08"},
                "published_date": "2026-11-08",
                "url": "https://www.csie.ncu.edu.tw/p/404-1000-88888.php",
                "attachment_url": None,
                "is_pinned": False,
            },
            {
                "announcement_id": "dept_chinese:001",
                "scope": "DEPARTMENT",
                "source_id": "dept_chinese",
                "category": "系所公告",
                "unit_name": "中國文學系",
                "college_id": None,
                "college_name": "文學院",
                "department_id": None,
                "department_name": "中國文學系",
                "affiliations": ["ALL"],
                "title": "紅樓夢專題講座",
                "summary": {"發布單位": "中國文學系", "發布日期": "2026-11-02"},
                "published_date": "2026-11-02",
                "url": "https://chinese.lit.ncu.edu.tw/p/404-1000-77777.php",
                "attachment_url": None,
                "is_pinned": False,
            },
        ],
    }
    sync_announcements_to_db(session, scraped_data)
    state = session.get(AnnouncementSyncState, 1)
    assert state is not None
    state.last_synced_at = SYNCED_AT
    session.add(state)
    session.commit()


def test_announcements_unavailable_before_first_sync(client: TestClient):
    assert client.get("/api/v1/announcements/status").status_code == 503
    assert client.get("/api/v1/announcements").status_code == 503
    assert client.get("/api/v1/announcements/affiliation").status_code == 503
    assert client.get("/api/v1/announcements/department").status_code == 503


def test_announcement_status_endpoint(client: TestClient, synced_announcements):
    res = client.get("/api/v1/announcements/status")
    assert res.status_code == 200
    data = res.json()
    assert data["total_count"] == 4
    assert data["affiliation_count"] == 2
    assert data["department_count"] == 2
    assert datetime.fromisoformat(data["last_updated"]) == SYNCED_AT


def test_affiliation_announcements_filtering_and_sorting(
    client: TestClient, synced_announcements
):
    # Pinned item (oaa:002) should appear before unpinned item (scholarship:001)
    res = client.get("/api/v1/announcements/affiliation")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] == 2
    assert [a["announcement_id"] for a in data["announcements"]] == [
        "oaa:002",
        "scholarship:001",
    ]

    # Filter for GRADUATE: includes "ALL" (oaa:002), excludes UNDERGRADUATE-only (scholarship:001)
    grad_res = client.get(
        "/api/v1/announcements/affiliation", params={"affiliation": "GRADUATE"}
    ).json()
    assert grad_res["total"] == 1
    assert grad_res["announcements"][0]["announcement_id"] == "oaa:002"

    # Filter by category
    scholar_res = client.get(
        "/api/v1/announcements/affiliation", params={"category": "獎學金"}
    ).json()
    assert scholar_res["total"] == 1
    assert scholar_res["announcements"][0]["title"] == "優秀大學部學生獎學金"


def test_department_announcements_links_catalog_department_and_aliases(
    client: TestClient, synced_announcements
):
    # Match using abbreviated name "資工系" or full name "資訊工程學系"
    for query_name in ("資工系", "資訊工程學系"):
        res = client.get(
            "/api/v1/announcements/department",
            params={"department_name": query_name},
        )
        assert res.status_code == 200
        data = res.json()
        assert data["total"] == 1
        item = data["announcements"][0]
        assert item["announcement_id"] == "dept_csie:001"
        assert item["department_id"] == "dept4001"
        assert item["college_id"] == "collegeI4"
        assert item["college_name"] == "資訊電機學院"

    # Filter by department_id directly
    by_id = client.get(
        "/api/v1/announcements/department", params={"department_id": "dept1001"}
    ).json()
    assert by_id["total"] == 1
    assert by_id["announcements"][0]["announcement_id"] == "dept_chinese:001"

    # Sources summary endpoint
    sources = client.get("/api/v1/announcements/departments").json()
    csie_source = next(s for s in sources if s["source_id"] == "dept_csie")
    assert csie_source["department_id"] == "dept4001"
    assert csie_source["announcement_count"] == 1


def test_sync_prunes_stale_items_only_for_succeeded_sources(
    session: Session, synced_announcements
):
    # Re-sync only `oaa` with a new item; `scholarship` is not in succeeded_sources
    sync_announcements_to_db(
        session,
        {
            "succeeded_sources": ["oaa"],
            "announcements": [
                {
                    "announcement_id": "oaa:003",
                    "scope": "AFFILIATION",
                    "source_id": "oaa",
                    "category": "教務",
                    "unit_name": "教務處",
                    "affiliations": ["ALL"],
                    "title": "選課更正公告",
                    "summary": {},
                    "published_date": "2026-11-10",
                    "url": "https://pdc.adm.ncu.edu.tw/p/404-1000-99999.php",
                }
            ],
        },
    )
    assert session.get(Announcement, "oaa:002") is None
    assert session.get(Announcement, "oaa:003") is not None
    # Unsynced source `scholarship` is preserved
    assert session.get(Announcement, "scholarship:001") is not None


def test_parse_scholarship_announcements():
    html = """
    <table class="news_list">
      <tr><th>序號</th><th>類別</th><th>名稱</th><th>內容摘要</th></tr>
      <tr>
        <td>1</td>
        <td>獎學金</td>
        <td>碩博士研究獎學金</td>
        <td>
          開始日期 : 2026-09-01<br/>
          結束日期 : 2026-10-15<br/>
          申請資格：限碩士班及博士班學生<br/>
          <a href="../Scholarship/files/grad.pdf">下載</a>
        </td>
      </tr>
      <tr>
        <td>2</td>
        <td>招募資訊</td>
        <td>國際處工讀生招募</td>
        <td>
          開始日期: 115/09/10<br/>
          申請資格: 外籍生或大學部學生皆可
        </td>
      </tr>
    </table>
    """
    items = parse_scholarship_announcements(html)
    assert len(items) == 2

    first = items[0]
    assert first["scope"] == "AFFILIATION"
    assert first["category"] == "獎學金"
    assert first["title"] == "碩博士研究獎學金"
    assert first["published_date"] == "2026-09-01"
    assert first["affiliations"] == ["GRADUATE"]
    assert (
        first["attachment_url"] == "https://cis.ncu.edu.tw/Scholarship/files/grad.pdf"
    )
    assert first["summary"]["開始日期"] == "2026-09-01"

    second = items[1]
    assert second["category"] == "招募資訊"
    assert second["published_date"] == "2026-09-10"
    assert set(second["affiliations"]) == {"INTERNATIONAL", "UNDERGRADUATE"}
    assert "ChooseType=parttime" in second["url"]


def test_parse_rpage_announcements():
    source = AnnouncementSourceConfig(
        source_id="dept_csie",
        scope="DEPARTMENT",
        unit_name="資訊工程學系",
        category="系所公告",
        url="https://www.csie.ncu.edu.tw/p/403-1000-1-1.php?Lang=zh-tw",
        college_name="資訊電機學院",
        department_name="資訊工程學系",
    )
    html = """
    <div class="mouter">
      <table>
        <tbody>
          <tr>
            <td data-th="標題">
              <span class="PtTop">[置頂]</span>
              <div class="mtitle">
                <a href="/p/404-1000-10101.php?Lang=zh-tw">115學年度碩士班甄試面試名單</a>
              </div>
            </td>
            <td data-th="日期"><span class="mdate">2026-11-08</span></td>
          </tr>
          <tr>
            <td>
              <div class="mtitle">
                <a href="https://www.csie.ncu.edu.tw/p/404-1000-10102.php">專題演講：大型語言模型系統</a>
              </div>
            </td>
            <td><i class="mdate">115/11/01</i></td>
          </tr>
        </tbody>
      </table>
    </div>
    """
    items = parse_rpage_announcements(html, source)
    assert len(items) == 2

    assert items[0]["scope"] == "DEPARTMENT"
    assert items[0]["department_name"] == "資訊工程學系"
    assert items[0]["college_name"] == "資訊電機學院"
    assert items[0]["title"] == "115學年度碩士班甄試面試名單"
    assert items[0]["published_date"] == "2026-11-08"
    assert items[0]["is_pinned"] is True
    assert (
        items[0]["url"] == "https://www.csie.ncu.edu.tw/p/404-1000-10101.php?Lang=zh-tw"
    )

    assert items[1]["title"] == "專題演講：大型語言模型系統"
    assert items[1]["published_date"] == "2026-11-01"
    assert items[1]["is_pinned"] is False


def test_normalize_date_str():
    assert normalize_date_str("2026-11-09") == "2026-11-09"
    assert normalize_date_str("發布日期：2026/05/07") == "2026-05-07"
    assert normalize_date_str("115-09-01") == "2026-09-01"
    assert normalize_date_str("無日期") is None
