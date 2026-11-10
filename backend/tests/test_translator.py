import json

import httpx
import pytest

from app.services import translator


@pytest.mark.parametrize(
    ("tag", "code"),
    [
        ("zh", "zt"),
        ("zh-TW", "zt"),
        ("zh-Hant-TW", "zt"),
        ("zh-CN", "zh"),
        ("zh-Hans", "zh"),
        ("vi", "vi"),
        ("en-US", "en"),
    ],
)
def test_engine_code(tag, code):
    assert translator.engine_code(tag) == code


def test_fetch_sends_the_batch_and_returns_its_translations():
    sent = []

    def handler(request: httpx.Request) -> httpx.Response:
        sent.append(json.loads(request.content))
        return httpx.Response(200, json={"translatedText": ["Hello", "World"]})

    with httpx.Client(transport=httpx.MockTransport(handler)) as client:
        assert translator._fetch(client, ["你好", "世界"], "zh", "en") == [
            "Hello",
            "World",
        ]
    assert sent == [
        {"q": ["你好", "世界"], "source": "zt", "target": "en", "format": "text"}
    ]


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(400, json={"error": "zt is not supported"}),
        httpx.Response(200, json={"translatedText": ["only one"]}),
        httpx.Response(200, text="not json"),
    ],
)
def test_fetch_gives_up_on_a_refusal_or_a_short_answer(response):
    transport = httpx.MockTransport(lambda _: response)
    with httpx.Client(transport=transport) as client:
        assert translator._fetch(client, ["你好", "世界"], "zh", "en") is None
