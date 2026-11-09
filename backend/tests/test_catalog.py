from datetime import UTC, datetime, timedelta

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app.api.deps import get_db
from app.main import app
from app.models.catalog import CatalogState, CourseDetail
from app.models.course import College, Course, Department
from app.services import course_detail as course_detail_service
from app.services.course_detail import parse_course_detail
from app.tasks.course_scraper import parse_current_semester

SYNCED_AT = datetime(2026, 11, 1, 8, 0, tzinfo=UTC)

DETAIL = {
    "objectives": "學會讀",
    "content": "第一週\n第二週",
    "books": "",
    "teaching_method": "講授",
    "grading_policy": "期末 100%",
    "distribution_conditions": [{"priority": 1, "rule": "學制:限學士班。"}],
}


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


@pytest.fixture(name="synced")
def synced_fixture(session: Session):
    session.add(College(college_id="collegeI0", college_name="文學院(120)"))
    session.add(College(college_id="collegeI1", college_name="工學院"))
    session.add(
        Department(
            department_id="dept1001", department_name="中文系", college_id="collegeI0"
        )
    )
    session.add(
        Department(
            department_id="dept2001", department_name="機械系", college_id="collegeI1"
        )
    )
    session.add(
        Course(
            serial_no=1001,
            class_no="CL1001-A",
            title="國文",
            credit=3.0,
            password_card="OPTIONAL",
            teachers=["張教授"],
            class_times=["1-1", "1-2"],
            limit_cnt=50,
            admit_cnt=40,
            wait_cnt=2,
            course_type="REQUIRED",
            college_ids=["collegeI1", "collegeI0"],
            department_ids=["dept2001", "dept1001"],
        )
    )
    # The scraper can leave a course with nothing but its identity.
    session.add(
        Course(
            serial_no=36019,
            class_no="ME6019-*",
            title="專題",
            password_card="",
            limit_cnt=None,
            admit_cnt=None,
            wait_cnt=None,
            course_type="通識",
            college_ids=[],
            department_ids=["deptGONE"],
        )
    )
    session.add(CatalogState(semester="115-1", last_synced_at=SYNCED_AT))
    session.commit()


@pytest.fixture(name="ncu")
def ncu_fixture(monkeypatch: pytest.MonkeyPatch):
    """Stand in for the NCU detail page; `calls` records each fetch."""

    class Ncu:
        result: dict | None | Exception = DETAIL
        calls: list[str] = []

    ncu = Ncu()
    ncu.calls = []

    def fake_fetch(serial_no: str, timeout: float = 12.0):
        ncu.calls.append(serial_no)
        if isinstance(ncu.result, Exception):
            raise ncu.result
        return ncu.result

    monkeypatch.setattr(course_detail_service, "fetch_course_detail", fake_fetch)
    return ncu


def test_catalog_is_unavailable_before_first_sync(client: TestClient):
    assert client.get("/api/v1/catalog").status_code == 503
    assert client.get("/api/v1/catalog/115-1/courses").status_code == 503


def test_catalog_status_reports_semester(client: TestClient, synced):
    data = client.get("/api/v1/catalog").json()
    assert data["semester"] == "115-1"
    assert data["course_count"] == 2
    assert datetime.fromisoformat(data["last_updated"]) == SYNCED_AT


def test_catalog_matches_app_contract(client: TestClient, synced):
    response = client.get("/api/v1/catalog/115-1/courses")
    assert response.status_code == 200
    data = response.json()

    assert data["academic_year"] == "115"
    assert data["semester"] == "1"
    assert datetime.fromisoformat(data["last_updated"]) == SYNCED_AT
    assert data["courses"][0] == {
        "serial_no": "01001",
        "class_no": "CL1001-A",
        "title": "國文",
        "credit": 3.0,
        "password_card": "OPTIONAL",
        "teachers": ["張教授"],
        "class_times": ["1-1", "1-2"],
        "limit_cnt": 50,
        "admit_cnt": 40,
        "wait_cnt": 2,
        # Cross-listed: the last listing department wins; the count is stripped.
        "college_name": "文學院",
        "department_name": "中文系",
        "course_type": "REQUIRED",
        "detail_url": "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=01001",
    }


def test_catalog_fills_every_required_field_for_sparse_courses(
    client: TestClient, synced
):
    sparse = client.get("/api/v1/catalog/115-1/courses").json()["courses"][1]
    assert sparse["serial_no"] == "36019"
    assert sparse["password_card"] == "NONE"
    assert sparse["course_type"] == "UNKNOWN"
    assert (sparse["limit_cnt"], sparse["admit_cnt"], sparse["wait_cnt"]) == (0, 0, 0)
    assert sparse["college_name"] == ""
    assert sparse["department_name"] == ""
    assert sparse["teachers"] == []


def test_other_semester_is_not_found(client: TestClient, synced):
    response = client.get("/api/v1/catalog/114-2/courses")
    assert response.status_code == 404
    assert "115-1" in response.json()["detail"]


def test_detail_is_fetched_once_then_served_from_cache(client: TestClient, synced, ncu):
    url = "/api/v1/catalog/115-1/courses/01001/detail"
    first = client.get(url)
    second = client.get(url)

    assert first.status_code == 200
    assert first.json() == {"serial_no": "01001", **DETAIL}
    assert second.json() == first.json()
    assert ncu.calls == ["01001"]


def test_expired_detail_is_refetched(client: TestClient, session: Session, synced, ncu):
    session.add(
        CourseDetail(
            serial_no=1001,
            objectives="舊",
            fetched_at=datetime.now(UTC) - timedelta(days=3),
        )
    )
    session.commit()

    data = client.get("/api/v1/catalog/115-1/courses/01001/detail").json()
    assert data["objectives"] == "學會讀"
    assert ncu.calls == ["01001"]


def test_stale_detail_is_served_when_ncu_is_down(
    client: TestClient, session: Session, synced, ncu
):
    session.add(
        CourseDetail(
            serial_no=1001,
            objectives="舊",
            fetched_at=datetime.now(UTC) - timedelta(days=3),
        )
    )
    session.commit()
    ncu.result = httpx.ConnectError("down")

    response = client.get("/api/v1/catalog/115-1/courses/01001/detail")
    assert response.status_code == 200
    assert response.json()["objectives"] == "舊"


def test_detail_errors(client: TestClient, synced, ncu):
    base = "/api/v1/catalog/115-1/courses"
    assert client.get(f"{base}/99999/detail").status_code == 404
    assert client.get(f"{base}/abc/detail").status_code == 404
    assert client.get("/api/v1/catalog/114-2/courses/01001/detail").status_code == 404
    assert ncu.calls == []

    ncu.result = httpx.ConnectError("down")
    assert client.get(f"{base}/01001/detail").status_code == 502

    ncu.result = None  # the page exists but holds no syllabus
    assert client.get(f"{base}/01001/detail").status_code == 404


def test_parse_course_detail():
    html = """
    <table class="classBase">
      <tr><td class="subTitle">課程目標</td><td>學會讀<br/>學會寫</td></tr>
      <tr><td class="subTitle">授課內容</td><td>No Data</td></tr>
      <tr><td class="subTitle">評量配分比重</td><td> 期末 100% </td></tr>
      <tr><td>無關</td><td>忽略</td></tr>
    </table>
    <table>
      <tr><th>選課分發條件</th></tr>
      <tr><td><table>
        <tr><td>優先順序</td><td>條件</td></tr>
        <tr><td>1</td><td>學制:限學士班。</td></tr>
        <tr><td>-</td><td>非數字順位</td></tr>
      </table></td></tr>
    </table>
    """
    assert parse_course_detail(html) == {
        "objectives": "學會讀\n學會寫",
        "content": "",
        "books": "",
        "teaching_method": "",
        "grading_policy": "期末 100%",
        "distribution_conditions": [{"priority": 1, "rule": "學制:限學士班。"}],
    }
    assert parse_course_detail("<html><body>查無資料</body></html>") is None


def test_parse_current_semester():
    html = """
    <select id="year" name="year">
      <option value="all">請選擇</option>
      <option selected VALUE='115'>115</option>
      <option VALUE='114'>114</option>
    </select>
    <select id="fall_spring" NAME="fall_spring">
      <option selected VALUE="1">上</option><option VALUE="2">下</option>
    </select>
    """
    assert parse_current_semester(html) == "115-1"
    assert parse_current_semester("<select id='year'></select>") is None
