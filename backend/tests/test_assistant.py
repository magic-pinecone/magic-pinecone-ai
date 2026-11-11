"""The assistant: where a question is sent, and what comes back with and without a model."""

import httpx
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.services import assistant, tdx
from app.services.answer_text import refusal_text
from app.services.tdx import TdxClient
from tests.test_transit import BEFORE_SERVICE, FakeClock, FakeTdx


class ScriptedModel:
    name = "scripted"

    def __init__(self, *replies: str):
        self.replies = list(replies)
        self.calls: list[list[dict]] = []

    def chat(self, messages, max_tokens=512):
        self.calls.append(messages)
        return self.replies.pop(0)


@pytest.fixture(name="fake_tdx")
def fake_tdx_fixture(monkeypatch: pytest.MonkeyPatch):
    fake = FakeTdx(BEFORE_SERVICE)
    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(
        tdx,
        "tdx_client",
        TdxClient(transport=httpx.MockTransport(fake), clock=FakeClock()),
    )
    return fake


@pytest.fixture(name="no_tdx")
def no_tdx_fixture(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(settings, "TDX_CLIENT_ID", None)
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", None)
    monkeypatch.setattr(tdx, "tdx_client", TdxClient())


def test_without_a_model_a_document_question_only_searches():
    result = assistant.ask("Cathay Insurance clinic visit reimbursement")
    assert result["kind"] == "documents" and result["passages"]
    assert result["answer"] is None and result["checks"] is None
    assert result["search_query"] == result["question"] and not result["translated"]


# Questions an independent review found sent to a building or a bus by looser rules:
# amounts and room numbers that look like routes, "bisa" and "business" read as
# bus words, abbreviations read as building codes, long questions that merely
# mention an office. All are about the documents.
DOCUMENT_QUESTIONS = [
    "The fee is NT$ 132 per month, correct?",
    "Do I have to pay 132 NTD for the ARC photo?",
    "I paid 133 dollars for insurance, can I get a refund?",
    "My dorm room is 132, how do I pay the dormitory fee?",
    "I need 133 credits to graduate, does the scholarship continue?",
    "I am 173 cm tall, is that needed on the health check form?",
    "Is the scholarship 9025 NTD per month?",
    "保險費是132元嗎？",
    "我住宿舍133房，居留證地址要怎麼填？",
    "Học bổng 132 đô la một tháng phải không?",
    "Biaya asuransi naik menjadi 133 tahun ini?",
    "Apakah saya bisa memperpanjang ARC di kantor imigrasi?",
    "Apakah mahasiswa bisa dijemput di bandara?",
    "Apakah beasiswa bisa dipakai untuk tiket pesawat ke bandara?",
    "Can I run a business while holding a student ARC from immigration?",
    "What are the business hours of the immigration office?",
    "Do I have to go to immigration to extend my ARC?",
    "Does the scholarship cover transport from the airport?",
    "Which hospital in Zhongli can I go to with my NHI card?",
    "我住在中壢，搭車去醫院看病可以用健保嗎？",
    "Tôi cần mang gì khi đi đến Sở di trú để gia hạn ARC?",
    "Do I need form I-20 or something similar for the visa?",
    "I am from HK, do I need an ARC?",
    "Do I need TOCFL C2 to graduate?",
    "Is the S1 semester scholarship paid in September?",
    "My ARC expires 2026-01-32, room A-203 is written on my letter, what do I do?",
    "Where can I get medical treatment covered by NHI?",
    "Where can I find the OIA scholarship application form?",
    "Where do I pay the dormitory fee?",
    "Where can I get a food allowance?",
    "How to apply for a work permit? I'm in the Mathematics department, where is the form?",
    "獎學金在哪裡申請？是國際事務處嗎？",
    "電機系的獎學金在哪裡申請？",
    "Nộp hồ sơ học bổng ở đâu, tại OIA phải không?",
    "Dimana saya bisa mengurus ARC, apakah di OIA?",
    "去移民署要帶什麼文件？",
    "What documents do I need to bring when I go to the Immigration Agency?",
    "Where do I go to apply for the scholarship at the OIA?",
    "獎學金要去哪裡申請？",
    "How do I apply for an ARC at the immigration office?",
    "What is the Cathay Insurance clinic visit reimbursement?",
    "What are the library fines?",
    "How many hours can I work with a work permit?",
]
PLACE_QUESTIONS = [
    ("Where is E6-A203?", "engineering-building-5"),
    ("e6 在哪裡？", "engineering-building-5"),
    ("E6在哪", "engineering-building-5"),
    ("Phòng E6 ở đâu?", "engineering-building-5"),
    ("Di mana gedung E6?", "engineering-building-5"),
    ("E6-A203", "engineering-building-5"),
    ("Where is room S-132?", "science-building-1"),
    ("Where is TR-133?", "teaching-research-building"),
    ("Where is LH?", "history-museum-lecture-hall"),
    ("Where is the library?", "main-library"),
    ("圖書館在哪裡？", "main-library"),
    ("where is the post office", "ncu-post-office"),
    ("Where is the language center?", "language-center"),
    ("Where is the lake?", "ncu-lake"),
    ("綜教館在哪裡？", "language-center"),
    ("Where is the OIA?", "teaching-research-building"),
    ("請問國際事務處在哪裡？", "teaching-research-building"),
    ("Where is the Mathematics department?", "hong-jing-hall"),
    ("Is A-203 in building A? where", "liberal-arts-building-1"),
]
BUS_QUESTIONS = [
    ("When is the next 132 bus?", ["132"]),
    ("132公車什麼時候來", ["132"]),
    ("Xe buýt 172 khi nào đến?", ["172"]),
    ("Bus 9025a ke Taipei jam berapa?", ["9025A"]),
    ("下一班到高鐵的公車幾點？", ["172", "173"]),
    ("How do I get to the airport?", ["172", "173"]),
    ("Bagaimana cara ke bandara naik bus?", ["172", "173"]),
    ("When is the next bus?", ["132", "172"]),
    ("下一班公車幾點？", ["132", "172"]),
    ("Which bus goes to the train station?", ["132", "133"]),
    ("How can I travel to Taoyuan airport?", ["172", "173"]),
    ("中壢火車站怎麼走", ["132", "133"]),
    ("Bagaimana ke kantor imigrasi dari kampus?", ["132", "133"]),
    ("How do I get to immigration to extend my ARC?", ["132", "133"]),
]


@pytest.mark.parametrize("question", DOCUMENT_QUESTIONS)
def test_a_document_question_stays_with_the_documents(question: str, no_tdx):
    assert assistant.ask(question)["kind"] == "documents"


@pytest.mark.parametrize(("question", "place_id"), PLACE_QUESTIONS)
def test_a_where_question_finds_the_building(question: str, place_id: str):
    result = assistant.ask(question)
    assert result["kind"] == "place" and result["places"][0].id == place_id
    assert result["passages"][0]["id"] == place_id


@pytest.mark.parametrize(("question", "routes"), BUS_QUESTIONS)
def test_a_bus_question_gets_its_routes(question: str, routes: list[str], fake_tdx):
    result = assistant.ask(question)
    assert result["kind"] == "bus" and result["bus_problem"] is None
    assert [entry["route"] for entry in result["bus"]] == routes


def test_a_one_letter_code_needs_its_room_and_a_word_is_not_a_code():
    assert assistant.places_by_code("Is a class in A-203 today?")[0].code == "A"
    assert assistant.places_by_code("Is a visa needed? I am an exchange student") == []
    assert assistant.places_by_code("form I-20") == []
    # "TR" is a building, "tr" in a sentence is not.
    assert [place.code for place in assistant.places_by_code("Room in TR")] == ["TR"]
    assert assistant.places_by_code("it is the best") == []


def test_a_number_is_a_route_only_beside_a_bus_word():
    assert assistant.bus_question("it costs nt$ 132 or 133 dollars") is None
    routes, wanted = assistant.bus_question("132公車和 9025a 客運")
    assert routes == ["132", "9025A"] and wanted == []


def test_the_translation_and_the_question_are_read_separately(no_tdx):
    # "去" plus the translation's "immigration" must not add up to a bus question.
    model = ScriptedModel(
        "What documents do I need to bring when I go to the Immigration Agency?",
        "- Bring your passport [1]",
    )
    assert assistant.ask("去移民署要帶什麼文件？", model)["kind"] == "documents"


def test_live_arrivals_are_data_and_never_go_to_the_model(fake_tdx: FakeTdx):
    model = ScriptedModel()
    result = assistant.ask("When is the next 132 bus?", model)
    assert all(stop.on_campus for stop in result["bus"][0]["stops"])
    assert result["passages"] == [] and result["answer"] is None and not model.calls
    assert result["error"] is None and result["bus_problem"] is None


def test_a_stop_the_route_passes_twice_is_listed_once_with_the_sooner_bus(
    fake_tdx: FakeTdx,
):
    stops = assistant.ask("When is the next 132 bus?")["bus"][0]["stops"]
    names = [stop.stop_name.zh for stop in stops]
    assert len(names) == len(set(names)) and "中央大學正門" in names
    every = tdx.tdx_client.arrivals_with_positions("132", True)
    gate = [s.next_bus_time for s in every if s.stop_name.zh == "中央大學正門"]
    assert len(gate) == 2
    kept = next(s for s in stops if s.stop_name.zh == "中央大學正門")
    assert kept.next_bus_time == min(gate)


def test_a_destination_brings_its_directions_and_its_routes(fake_tdx: FakeTdx):
    model = ScriptedModel("high-speed rail bus", "- 搭 172 或 173 [1]")
    result = assistant.ask("下一班到高鐵的公車幾點？", model)
    assert [d.id for d in result["destinations"]] == ["thsr-taoyuan-station"]
    assert [entry["route"] for entry in result["bus"]] == ["172", "173"]
    # The model read the directions only; the arrivals stay out of its prompt.
    assert [p["id"] for p in result["passages"]] == ["thsr-taoyuan-station"]
    assert "172" in model.calls[1][1]["content"]
    assert "arrives in" not in model.calls[1][1]["content"]
    assert result["checks"]["citations_valid"]


def test_without_a_tdx_key_the_model_is_not_asked_to_guess(no_tdx):
    model = ScriptedModel()
    result = assistant.ask("When is the next 132 bus?", model)
    assert result["kind"] == "bus" and result["bus_problem"] == "not_configured"
    assert result["answer"] is None and not model.calls


def test_other_languages_are_translated_for_the_search_first():
    model = ScriptedModel("Cathay insurance clinic visit", refusal_text("zh-TW"))
    result = assistant.ask("國泰保險每次門診最高可以理賠多少錢？", model)
    assert result["translated"]
    assert result["search_query"] == "Cathay insurance clinic visit"
    assert result["passages"], "the translation finds what the Chinese cannot"
    assert result["checks"]["refused"] and not result["checks"]["unsupported_numbers"]
    assert "Passages:" in model.calls[1][1]["content"]


def test_a_number_missing_from_the_passages_is_reported():
    model = ScriptedModel("- You get NT$987,654 per visit [1]")
    result = assistant.ask(
        "What is the Cathay Insurance clinic visit reimbursement?", model
    )
    assert "987654" in "".join(result["checks"]["unsupported_numbers"]).replace(",", "")


def test_a_citation_of_a_passage_that_is_not_there_is_invalid():
    model = ScriptedModel("- Ask the office [9]")
    result = assistant.ask("How many hours can I work with a work permit?", model)
    assert not result["checks"]["citations_valid"]


def test_a_model_failure_is_reported_and_the_passages_are_kept():
    class Broken:
        name = "broken"

        def chat(self, *args, **kwargs):
            raise assistant.ModelError("HTTP 500")

    result = assistant.ask("work permit hours", Broken())
    assert result["error"] == "model endpoint: HTTP 500"
    assert result["answer"] is None and result["passages"]


def test_chat_model_sends_the_request_and_reads_the_content():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"], seen["auth"] = str(request.url), request.headers["authorization"]
        seen["body"] = request.read()
        return httpx.Response(200, json={"choices": [{"message": {"content": "hi"}}]})

    model = assistant.ChatModel("http://model.test/v1/", "m", "k", "none")
    model._http = httpx.Client(
        transport=httpx.MockTransport(handler), headers=model._http.headers
    )
    assert model.chat([{"role": "user", "content": "q"}]) == "hi"
    assert seen["url"] == "http://model.test/v1/chat/completions"
    assert seen["auth"] == "Bearer k" and b'"reasoning_effort":"none"' in seen["body"]


def test_chat_model_errors_never_carry_the_address():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, text="bad key k at http://model.test")

    model = assistant.ChatModel("http://model.test/v1", "m", "k")
    model._http = httpx.Client(transport=httpx.MockTransport(handler))
    with pytest.raises(assistant.ModelError) as raised:
        model.chat([])
    assert str(raised.value) == "HTTP 401"


def test_an_empty_answer_is_an_error_not_an_answer():
    result = assistant.ask(
        "How many hours can I work with a work permit?", ScriptedModel("")
    )
    assert result["answer"] is None and result["checks"] is None
    assert result["error"] == "model endpoint: empty answer" and result["passages"]


def test_chat_model_rejects_content_that_is_not_text():
    def handler(request: httpx.Request) -> httpx.Response:
        parts = [{"type": "text", "text": "hi"}]
        return httpx.Response(200, json={"choices": [{"message": {"content": parts}}]})

    model = assistant.ChatModel("http://model.test/v1", "m")
    model._http = httpx.Client(transport=httpx.MockTransport(handler))
    with pytest.raises(assistant.ModelError):
        model.chat([])


def test_looks_english():
    assert assistant.looks_english("How long is the ARC valid?")
    assert not assistant.looks_english("Thẻ cư trú có hiệu lực bao lâu?")
    assert not assistant.looks_english("居留證多久到期？")
    assert not assistant.looks_english("Berapa lama ARC berlaku?")


def test_office_terms_replace_literal_translations():
    assert assistant.office_terms("Guotai insurance limit") == "Cathay insurance limit"
    assert "ARC" in assistant.office_terms("How long is the residence permit valid?")
    already = "How do I extend my ARC residence permit?"
    assert assistant.office_terms(already) == already


def test_endpoint_without_a_model(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(assistant, "configured_model", lambda: None)
    response = TestClient(app).post(
        "/api/v1/assistant/ask", json={"question": "Where is LH?"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "place" and body["model"] is None and body["answer"] is None
    place = body["places"][0]
    assert place["code"] == "LH" and place["map_links"]["google_walking_directions"]


def test_endpoint_with_a_model_and_live_arrivals(
    monkeypatch: pytest.MonkeyPatch, fake_tdx: FakeTdx
):
    model = ScriptedModel()
    monkeypatch.setattr(assistant, "configured_model", lambda: model)
    response = TestClient(app).post(
        "/api/v1/assistant/ask", json={"question": "When is the next 132 bus?"}
    )
    body = response.json()
    assert response.status_code == 200 and body["model"] == "scripted"
    assert body["bus"][0]["route"] == "132" and body["bus"][0]["stops"]
    assert body["bus"][0]["source"].startswith("TDX")
    assert body["answer"] is None and body["checks"] is None and body["passages"] == []


def test_endpoint_rejects_an_empty_or_overlong_question():
    client = TestClient(app)
    assert (
        client.post("/api/v1/assistant/ask", json={"question": ""}).status_code == 422
    )
    long = "a" * (assistant.MAX_QUESTION_CHARS + 1)
    assert (
        client.post("/api/v1/assistant/ask", json={"question": long}).status_code == 422
    )
