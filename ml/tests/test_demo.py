"""The demo page serves the backend's assistant chain."""

from demo import app as demo
from fastapi.testclient import TestClient


class ScriptedModel:
    name = "scripted"

    def __init__(self, *replies: str):
        self.replies = list(replies)

    def chat(self, messages, max_tokens=512):
        return self.replies.pop(0)


def test_page_and_examples_are_served():
    client = TestClient(demo.create_app())
    assert "<title>" in client.get("/").text
    info = client.get("/api/info").json()
    assert info["model"] is None and info["documents"] >= 1
    languages = {example["lang"] for example in info["examples"]}
    assert languages == {"en", "zh-TW", "vi", "id"}


def test_without_a_model_a_building_question_still_finds_the_building():
    client = TestClient(demo.create_app())
    body = client.post("/api/ask", json={"question": "Where is E6-A203?"}).json()
    assert body["kind"] == "place" and body["answer"] is None
    assert body["places"][0]["map_links"]["google_walking_directions"]


def test_with_a_model_the_answer_and_its_checks_come_back():
    model = ScriptedModel("- Up to NT$1,000 per visit [1]")
    client = TestClient(demo.create_app(model))
    question = "What is the Cathay Insurance clinic visit reimbursement?"
    body = client.post("/api/ask", json={"question": question}).json()
    assert body["model"] == "scripted" and body["checks"]["cited"] == [1]
