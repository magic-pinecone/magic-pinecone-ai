import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine, select
from sqlmodel.pool import StaticPool

from app.api.deps import get_db
from app.main import app
from app.models.club import Club
from app.tasks.club_scraper import parse_club, save_clubs

RAW = {
    "clubID": 1049,
    "Ori_ID": "D009",
    "type": "A",
    "SortByType": 2,
    "club_name": "桃園地區校友會",
    "club_eName": "NCUTY",
    "address": "據德樓",
    "web_site": "",
    "description": "第一行<br> 第二行<BR/>",
    "description_en": None,
    "build_date": None,
    "status": "",
    "club_pic_path": "D009.jpg",
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


def club(club_id: int, category: str, sort_order: int, name: str) -> dict:
    return parse_club(
        {
            "clubID": club_id,
            "type": category,
            "SortByType": sort_order,
            "club_name": name,
            "club_pic_path": "",
        }
    )


def test_parse_club():
    parsed = parse_club(RAW)
    assert parsed["club_id"] == 1049
    assert parsed["category"] == "A"
    assert parsed["sort_order"] == 2
    assert parsed["description"] == "第一行\n第二行"
    assert parsed["description_en"] == ""
    assert parsed["picture_url"] == "https://club.adm.ncu.edu.tw/images/club/D009.jpg"
    assert club(1, "G", 1, "學生會")["picture_url"] == ""


def test_save_clubs_upserts_and_deletes(session: Session):
    save_clubs(session, [club(1, "A", 1, "舊名"), club(2, "B", 1, "會消失")])
    save_clubs(session, [club(1, "A", 1, "新名"), club(3, "C", 1, "新社團")])

    rows = {c.club_id: c.name for c in session.exec(select(Club)).all()}
    assert rows == {1: "新名", 3: "新社團"}


def test_save_clubs_refuses_empty(session: Session):
    save_clubs(session, [club(1, "A", 1, "社團")])
    with pytest.raises(ValueError):
        save_clubs(session, [])
    assert len(session.exec(select(Club)).all()) == 1


def test_list_clubs(client: TestClient, session: Session):
    save_clubs(
        session,
        [club(3, "B", 1, "學術"), club(2, "A", 2, "乙"), club(1, "A", 1, "甲")],
    )

    body = client.get("/api/v1/clubs").json()
    assert [c["name"] for c in body] == ["甲", "乙", "學術"]
    assert body[0]["category_name"] == "聯誼性"

    filtered = client.get("/api/v1/clubs", params={"category": "B"}).json()
    assert [c["club_id"] for c in filtered] == [3]


def test_list_clubs_translates_and_caches(
    client: TestClient, session: Session, monkeypatch: pytest.MonkeyPatch
):
    calls: list[str] = []

    def fake_fetch(_client, text: str, lang: str) -> str | None:
        calls.append(text)
        return None if text == "壞" else f"{lang}:{text}"

    monkeypatch.setattr("app.services.translator._fetch", fake_fetch)
    save_clubs(session, [club(1, "A", 1, "甲"), club(2, "A", 2, "壞")])

    body = client.get("/api/v1/clubs", params={"lang": "ja"}).json()
    assert [c["name"] for c in body] == ["ja:甲", "壞"]  # a failed text stays Chinese
    assert body[0]["category_name"] == "ja:聯誼性"
    assert sorted(calls) == ["壞", "甲", "聯誼性"]  # blanks skipped, category once

    calls.clear()
    client.get("/api/v1/clubs", params={"lang": "ja"})
    assert calls == ["壞"]  # only the uncached failure is retried

    calls.clear()
    assert (
        client.get("/api/v1/clubs", params={"lang": "zh-TW"}).json()[0]["name"] == "甲"
    )
    assert calls == []
    assert client.get("/api/v1/clubs", params={"lang": "ja&x"}).status_code == 422

    save_clubs(session, [{**club(1, "A", 1, "甲"), "name_en": "Jia"}])
    english = client.get("/api/v1/clubs", params={"lang": "en-US"}).json()[0]
    assert english["name"] == "Jia"  # the club's own English, not a machine translation
    assert english["category_name"] == "Social"
