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
from app.services.knowledge import crawler, sources
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


def test_food_places_match_the_app_copy():
    app_copy = (
        sources.FOOD_PLACES.parents[3].parent
        / "app/shared/src/commonMain/moko-resources/files/ncu_food_places.json"
    )
    if not app_copy.exists():
        pytest.skip("app sources are not checked out next to the backend")
    assert sources.FOOD_PLACES.read_bytes() == app_copy.read_bytes()


def test_keyword_search_finds_halal_places():
    found = knowledge_index.search(
        "halal restaurant", source_types=["food_place"], limit=3, embedder=None
    )

    assert found and all(passage["source_type"] == "food_place" for passage in found)
    assert any(passage["topic"] == "halal" for passage in found)


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
