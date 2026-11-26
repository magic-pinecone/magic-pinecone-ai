import json

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app.api.deps import get_db
from app.core.config import settings
from app.main import app
from app.models.club import Club
from app.models.course import College, Course, Department
from app.services.knowledge import calendar_pdf, crawler, sources
from app.services.knowledge import index as knowledge_index
from app.services.knowledge.embedder import EmbeddingClient


def fake_embeddings(request: httpx.Request) -> httpx.Response:
    texts = json.loads(request.content)["input"]
    meanings = [("permit",), ("work", "工作"), ("credit", "學分"), ("club", "社團")]
    rows = []
    for index, text in enumerate(texts):
        folded = text.casefold()
        vector = [float(sum(folded.count(w) for w in words)) for words in meanings]
        vector.append(0.1)
        rows.append({"index": index, "embedding": vector})
    return httpx.Response(200, json={"data": rows})


@pytest.fixture
def embedder():
    return EmbeddingClient(
        "http://embedder/v1", "fake", transport=httpx.MockTransport(fake_embeddings)
    )


@pytest.fixture(name="session")
def session_fixture():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    SQLModel.metadata.create_all(engine)
    with Session(engine) as session:
        session.add(College(college_id="c1", college_name="理學院"))
        session.add(
            Department(department_id="d1", department_name="數學系", college_id="c1")
        )
        session.add(
            Course(
                serial_no=3001,
                class_no="MA1001-A",
                title="微積分",
                credit=4.0,
                teachers=["王老師"],
                class_times=["3-2", "3-3"],
                course_type="REQUIRED",
                department_ids=["d1"],
            )
        )
        session.add(
            Club(
                club_id=7,
                category="B",
                name="天文社",
                name_en="Astronomy Club",
                description="觀星 stargazing",
            )
        )
        session.commit()
        yield session


@pytest.fixture(autouse=True)
def restore_index():
    yield
    knowledge_index._current = None


@pytest.fixture(name="client")
def client_fixture(session: Session):
    app.dependency_overrides[get_db] = lambda: session
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_crawler_keeps_only_oia_pages_and_pdfs():
    assert crawler.normalize("/p/412-1026-2204.php?Lang=en&x=1") == (
        "https://oia.ncu.edu.tw/p/412-1026-2204.php?Lang=en"
    )
    assert crawler.normalize("https://oia.ncu.edu.tw/static/file/26/a.pdf#page=2") == (
        "https://oia.ncu.edu.tw/static/file/26/a.pdf"
    )
    assert crawler.normalize("/") == "https://oia.ncu.edu.tw/index.php"
    assert crawler.normalize("https://www.facebook.com/ncuoia/") is None
    assert crawler.normalize("/static/file/26/style.css") is None


def test_crawler_reads_lines_and_links_from_the_content():
    html = (
        "<html><title>Work - Office of International Affairs</title><body>"
        "<nav><a href='/p/412-1026-1.php?Lang=en'>Menu</a></nav>"
        "<div class='mpgdetail'><p>Up to <b>20</b> hours a week.</p>"
        "<table><tr><td>Fee</td><td>NT$100</td></tr></table>"
        "<a href='/static/file/26/form.pdf'>Form</a></div></body></html>"
    )

    title, lines, links = crawler.parse_page(html, "https://oia.ncu.edu.tw/p/1.php")

    assert title == "Work"
    assert lines == [
        "Up to 20 hours a week.",
        "Fee | NT$100",
        "Form",
        "<https://oia.ncu.edu.tw/static/file/26/form.pdf>",
    ]
    assert "https://oia.ncu.edu.tw/static/file/26/form.pdf" in links


def test_every_file_source_type_is_in_the_index():
    loaded = {document.source_type for document in sources.file_documents()}

    assert loaded == set(sources.SOURCE_TYPES) - {"course", "club"}


def test_keyword_search_matches_the_old_oia_search():
    found = knowledge_index.search("work permit hours per week", limit=1, embedder=None)

    assert found[0]["id"] == "oia-work-permit"
    assert found[0]["retrieval"] == "keyword"


def test_refresh_adds_courses_and_clubs_and_embeds_once(session, embedder):
    first = knowledge_index.refresh(session, embedder=embedder, embed_missing=True)
    second = knowledge_index.refresh(session, embedder=embedder, embed_missing=True)

    counts = {row["source_type"]: row for row in first["sources"]}
    assert counts["course"]["documents"] == 1
    assert counts["club"]["documents"] == 1
    assert first["embedded"] > 0
    assert second["embedded"] == 0
    assert counts["course"]["embedded_passages"] == 1
    course = knowledge_index.get_document("course-3001")
    text = "\n".join(course.lines)
    assert "Wed 週三 periods 2,3" in text
    assert course.title == "微積分 Calculus (MA1001-A)"
    assert "Tên môn học: Phép tính" in text
    assert "數學系 (English: Department of Mathematics)" in text
    assert len(course.lines) <= knowledge_index.PASSAGE_LINES


def test_vector_search_finds_passages_without_shared_words(
    session, embedder, monkeypatch
):
    knowledge_index.refresh(session, embedder=embedder, embed_missing=True)
    monkeypatch.setattr(settings, "KNOWLEDGE_MIN_SIMILARITY", 0.5)

    found = knowledge_index.search(
        "社團", source_types=["club"], limit=1, embedder=embedder
    )

    assert found[0]["id"] == "club-7"
    assert found[0]["similarity"] is not None


def test_search_endpoint_returns_passages(client):
    response = client.get(
        "/api/v1/knowledge/search",
        params={"q": "128 credits", "source_types": "credits", "limit": 2},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["source_types"] == ["credits"]
    assert set(body["passages"][0]) == {
        "id",
        "title",
        "topic",
        "source_type",
        "lang",
        "url",
        "fetched_on",
        "matched_words",
        "similarity",
        "score",
        "retrieval",
        "passage",
    }
    assert {p["source_type"] for p in body["passages"]} == {"credits"}


def test_search_endpoint_rejects_an_unknown_source_type(client):
    response = client.get(
        "/api/v1/knowledge/search", params={"q": "x", "source_types": "rumours"}
    )

    assert response.status_code == 422


def test_document_endpoint(client):
    found = client.get("/api/v1/knowledge/documents/oia-work-permit")
    missing = client.get("/api/v1/knowledge/documents/nope")

    assert found.status_code == 200
    assert found.json()["source_type"] == "oia_page"
    assert "Work Permit" in found.json()["text"]
    assert missing.status_code == 404


def test_reindex_and_sources_endpoints(client, monkeypatch):
    monkeypatch.setattr(knowledge_index, "configured_embedder", lambda: None)

    reindexed = client.post("/api/v1/knowledge/reindex", params={"wait": True})
    listed = client.get("/api/v1/knowledge/sources")

    assert reindexed.status_code == 200
    assert reindexed.json()["with_database"] is True
    assert reindexed.json()["embedded"] == 0
    rows = {row["source_type"]: row for row in listed.json()["sources"]}
    assert rows["course"]["documents"] == 1
    assert listed.json()["embedding_model"] is None


def test_food_places_cover_halal_and_nearby_places():
    documents = sources.food_place_documents()
    data = json.loads(sources.FOOD_PLACES.read_text(encoding="utf-8"))
    halal = [d for d in documents if d.topic == "halal"]

    assert len(documents) == len(data["halal"]) + len(data["nearby"])
    assert len({d.id for d in documents}) == len(documents)
    assert len(halal) == len(data["halal"])
    text = "\n".join(halal[0].lines)
    assert "清真認證餐廳" in text and "Certificates 認證:" in text
    assert "straight line from the NCU campus centre" in text


def test_food_place_distance_is_rounded_by_scale():
    assert sources._distance(434) == ("430 m", "430 公尺")
    assert sources._distance(1234) == ("1.2 km", "1.2 公里")
    assert round(sources._metres((24.9682, 121.1955), (24.9682, 121.1955))) == 0


def test_keyword_search_finds_halal_places():
    found = knowledge_index.search(
        "halal restaurant", source_types=["food_place"], limit=3, embedder=None
    )

    assert found and all(passage["source_type"] == "food_place" for passage in found)
    assert any(passage["topic"] == "halal" for passage in found)


def calendar_lines(month_id: str) -> str:
    documents = {d.id: d for d in sources.academic_calendar_documents()}
    return "\n".join(documents[month_id].lines)


def test_academic_calendar_has_a_document_per_month():
    documents = sources.academic_calendar_documents()
    ids = [d.id for d in documents]

    assert ids == sorted(ids) and len(set(ids)) == len(ids)
    assert ids[0] == "calendar-2026-08" and ids[-1] == "calendar-2027-07"
    assert all(d.source_type == "academic_calendar" for d in documents)
    assert all(d.url.endswith("calendar115.pdf") for d in documents)
    assert "calendar-2026-09" in ids
    document = next(d for d in documents if d.id == "calendar-2026-09")
    assert document.title == (
        "NCU academic calendar 115-1, September 2026 中央大學校曆 2026年9月"
    )


def test_academic_calendar_dates_land_in_their_months():
    expected = {
        "calendar-2026-08": ["2026-08-01 (Sat 週六, August 8月): 115學年度第1學期開始"],
        "calendar-2026-09": [
            "2026-09-02 to 2026-09-16 (Wed 週三 to Wed 週三, September 9月): 加退選",
            "2026-09-07 (Mon 週一, September 9月): 全校學生註冊、開始上課",
            "2026-09-25 (Fri 週五, September 9月): 中秋節放假1日",
        ],
        "calendar-2026-10": ["2026-10-10 (Sat 週六, October 10月): 國慶日放假1日"],
        "calendar-2026-11": ["2026-11-11 (Wed 週三, November 11月): 全校運動會"],
        "calendar-2026-12": [
            "2026-12-25 (Fri 週五, December 12月): 行憲紀念日放假1日",
            "2026-12-25 (Fri 週五, December 12月): 寒假開始",
            "2026-12-28 to 2027-01-29 (Mon 週一 to Fri 週五, "
            "December 12月 to January 1月): 寒修上課",
        ],
        "calendar-2027-01": [
            "2027-01-31 (Sun 週日, January 1月): 115學年度第1學期終了"
        ],
        "calendar-2027-02": [
            "2027-02-10 to 2027-02-24 (Wed 週三 to Wed 週三, February 2月): 加退選",
            "2027-02-15 (Mon 週一, February 2月): 全校學生註冊、開始上課",
        ],
        "calendar-2027-04": [
            "2027-04-04 (Sun 週日, April 4月): 兒童節放假1日",
            "2027-04-05 (Mon 週一, April 4月): 民族掃墓節放假1日",
        ],
        "calendar-2027-05": ["2027-05-22 (Sat 週六, May 5月): 全校畢業典禮"],
        "calendar-2027-06": [
            "2027-06-09 (Wed 週三, June 6月): 校慶紀念日",
            "2027-06-09 (Wed 週三, June 6月): 端午節放假1日",
        ],
        "calendar-2027-07": ["2027-07-31 (Sat 週六, July 7月): 115學年度第2學期終了"],
    }

    for month_id, wanted in expected.items():
        text = calendar_lines(month_id)
        for line in wanted:
            assert line in text, (month_id, line)


def test_academic_calendar_events_stay_in_their_semester():
    data = json.loads(
        next(sources.ACADEMIC_CALENDARS.glob("*.json")).read_text(encoding="utf-8")
    )
    events = data["events"]

    assert {e["semester"] for e in events} == {"115-1", "115-2"}
    assert all(e["start"] <= e["end"] for e in events)
    assert all("822次" not in e["text"] and "分機" not in e["text"] for e in events)
    firsts = [e["start"] for e in events if e["semester"] == "115-2"]
    assert min(firsts) == "2027-02-01" and max(firsts) == "2027-07-31"


def test_calendar_page_parses_ranges_and_rolls_the_month_over():
    page = (
        "年 月 週 日 一 二 三 四 五 六 記 事\n"
        "1 2 3\n"
        "1日　學期開始\n"
        "2 3 4 5 6 7 8 3日～9日　新生申請 28日～116年1月29日　寒修上課\n"
        "31日～9月4日　新生訓練週\n"
        "5 4 5 6 7 8 9 10 9日　國慶日補假1日 10日　國慶日放假1日\n"
        "30日～12月18日　導師輔導選課\n"
        "1日～14日　轉系申請\n"
        "3日　輔系、雙主修、學分學程、抵免審核申\n"
        "請\n"
        "11日　提前畢業申請截止　；　資格核定表繳交截止\n"
        "822次行政會議通過\n"
        "116\n年\n1\n月\n"
    )

    events = calendar_pdf.page_events(page, 2026, 8, "115-1")
    found = [(e["start"], e["end"], e["text"]) for e in events]

    assert found[0] == ("2026-08-01", "2026-08-01", "學期開始")
    assert found[1] == ("2026-08-03", "2026-08-09", "新生申請")
    assert found[2] == ("2026-08-28", "2027-01-29", "寒修上課")
    assert found[3] == ("2026-08-31", "2026-09-04", "新生訓練週")
    assert found[4][0] == "2026-09-09" and found[5][0] == "2026-09-10"
    assert found[6] == ("2026-09-30", "2026-12-18", "導師輔導選課")
    assert found[7] == ("2026-10-01", "2026-10-14", "轉系申請")
    assert found[8] == (
        "2026-10-03",
        "2026-10-03",
        "輔系、雙主修、學分學程、抵免審核申請",
    )
    assert [e["text"] for e in events[-2:]] == [
        "提前畢業申請截止",
        "資格核定表繳交截止",
    ]
    assert events[-1]["start"] == events[-2]["start"] == "2026-10-11"
    assert all(e["semester"] == "115-1" for e in events)


def test_calendar_year_rolls_over_in_december():
    page = "年 月 週 日\n30日　十二月\n1日　一月\n1日～2月3日　跨月\n"

    events = calendar_pdf.page_events(page, 2026, 12, "115-1")

    assert [e["start"] for e in events] == ["2026-12-30", "2027-01-01", "2027-01-01"]
    assert events[2]["end"] == "2027-02-03"


def test_keyword_search_finds_calendar_passages():
    found = knowledge_index.search(
        "加退選", source_types=["academic_calendar"], limit=5, embedder=None
    )

    assert found
    assert all(passage["source_type"] == "academic_calendar" for passage in found)
    assert any("加退選" in passage["passage"] for passage in found)


def test_syllabus_lines_carry_their_english_translation():
    table = {"認識光學": "Understand optics"}

    assert (
        sources._with_english("Objectives 課程目標", "認識光學", table)
        == "Objectives 課程目標: 認識光學 | English: Understand optics"
    )
    assert sources._with_english("Objectives 課程目標", "未翻譯", table) == (
        "Objectives 課程目標: 未翻譯"
    )
    assert sources._with_english("Objectives 課程目標", "", table) is None
