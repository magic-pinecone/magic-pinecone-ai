import httpx
import pytest

from app.services import translator


def test_rate_limit_pauses_the_endpoint(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(translator, "_paused_until", 0.0)
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(302, headers={"location": "https://www.google.com/sorry"})

    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert translator._fetch(client, "學生會", "vi") is None
        assert translator._fetch(client, "學生會", "vi") is None
    assert len(calls) == 1  # the second text never reaches Google while paused


def test_fetch_joins_sentences(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(translator, "_paused_until", 0.0)
    body = [[["Hello. ", "你好。"], ["World", "世界"], [None, None]]]
    transport = httpx.MockTransport(lambda _: httpx.Response(200, json=body))
    with httpx.Client(transport=transport) as client:
        assert translator._fetch(client, "你好。世界", "en") == "Hello. World"
