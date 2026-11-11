import asyncio
import json

import httpx
import pytest
from mcp.server.mcpserver.exceptions import ToolError

from app import mcp_server
from app.core.config import settings
from app.services import tdx


def call(tool: str, **arguments):
    """Call a tool through the MCP server, as a client would."""
    result = asyncio.run(mcp_server.mcp.call_tool(tool, arguments))
    assert not result.is_error
    return [json.loads(block.text) for block in result.content]


def test_server_lists_its_tools():
    tools = asyncio.run(mcp_server.mcp.list_tools())

    assert {tool.name for tool in tools} == {
        "get_graduation_language_requirements",
        "get_chinese_language_requirements",
        "search_international_programs",
        "search_official_documents",
        "search_student_services",
        "get_student_service_contact",
        "estimate_nhi_enrollment_date",
        "find_place",
        "get_bus_arrivals",
        "find_destination",
    }
    assert len(tools) == 10


def test_requirements_tool_returns_the_threshold_of_the_admission_year():
    (answer,) = call(
        "get_graduation_language_requirements",
        degree="bachelor",
        college="資訊電機學院",
        cohort=115,
    )

    (rule,) = answer["rules"]
    assert [t["value"] for t in rule["english"]["toeic_lr"]] == [
        "785（聽力 400、閱讀 385）"
    ]
    assert (
        answer["en"]["785（聽力 400、閱讀 385）"] == "785 (listening 400, reading 385)"
    )
    assert {s["id"] for s in answer["sources"]} == set(rule["sources"])
    assert all(source["url"].startswith("https://") for source in answer["sources"])


def test_requirements_tool_gives_the_graduate_overview_for_graduate_degrees():
    (answer,) = call("get_graduation_language_requirements", degree="master")

    assert "not_checked" in answer["overview"]
    mechanical = next(r for r in answer["rules"] if r["id"] == "master-mechanical")
    assert "外籍學位生不受此條文限制" in mechanical["international_students"][0]


def test_document_search_needs_every_word_and_returns_the_official_url():
    documents = call("search_official_documents", query="外籍生 TOCFL 入門級")

    assert {"env-master-rules", "env-phd-rules"} <= {d["id"] for d in documents}
    assert all(d["url"].startswith("https://") for d in documents)
    assert call("search_official_documents", query="外籍生 不存在的字串xyz") == []


def test_document_resource_serves_the_corpus_text():
    text = mcp_server.official_document("aa-english-proficiency-rules")

    assert "第三條" in text
    with pytest.raises(ValueError):
        mcp_server.official_document("../requirements")


def eta_row(uid: str, name: str, sequence: int, **fields) -> dict:
    # In the shape of TDX's EstimatedTimeOfArrival rows.
    return {
        "StopUID": uid,
        "StopName": {"Zh_tw": name, "En": name},
        "RouteName": {"Zh_tw": "132", "En": "132"},
        "Direction": 0,
        "StopSequence": sequence,
        "UpdateTime": "2026-11-10T16:01:36+08:00",
        **fields,
    }


ETA_ROWS = [
    eta_row(
        "TAO2548",
        "中壢公車站",
        1,
        StopStatus=1,
        NextBusTime="2026-11-10T16:30:00+08:00",
    ),
    eta_row(
        "TAO269",
        "中央大學正門",
        14,
        StopStatus=0,
        EstimateTime=1140,
        PlateNumb="EAL-0563",
    ),
]


def use_fake_tdx(monkeypatch: pytest.MonkeyPatch, data: httpx.Response) -> None:
    """Point the bus tool at a TDX that answers every data call with `data`."""

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/token"):
            return httpx.Response(200, json={"access_token": "t", "expires_in": 60})
        return data

    monkeypatch.setattr(settings, "TDX_CLIENT_ID", "id")
    monkeypatch.setattr(settings, "TDX_CLIENT_SECRET", "secret")
    monkeypatch.setattr(
        tdx, "tdx_client", tdx.TdxClient(transport=httpx.MockTransport(handler))
    )


def test_bus_tool_returns_campus_stops_by_default(monkeypatch: pytest.MonkeyPatch):
    use_fake_tdx(monkeypatch, httpx.Response(200, json=ETA_ROWS))

    (answer,) = call("get_bus_arrivals", route="132")

    assert answer["route"] == "132"
    assert answer["source"].startswith("TDX")
    (stop,) = answer["stops"]
    assert stop["stop_name"] == {"zh": "中央大學正門", "en": "NCU Main Gate"}
    assert stop["status"] == "EN_ROUTE"
    assert stop["estimate_seconds"] == 1140
    assert stop["plate_number"] == "EAL-0563"

    (whole_route,) = call("get_bus_arrivals", route="132", campus_only=False)
    assert [s["on_campus"] for s in whole_route["stops"]] == [False, True]
    assert whole_route["stops"][0]["next_bus_time"] == "2026-11-10T16:30:00+08:00"


def bus_tool_error(**arguments) -> str:
    with pytest.raises(ToolError) as raised:
        asyncio.run(mcp_server.mcp.call_tool("get_bus_arrivals", arguments))
    return str(raised.value)


def test_bus_tool_says_when_no_key_is_configured(monkeypatch: pytest.MonkeyPatch):
    use_fake_tdx(monkeypatch, httpx.Response(200, json=ETA_ROWS))
    monkeypatch.setattr(settings, "TDX_CLIENT_ID", None)

    message = bus_tool_error(route="132")

    assert "no TDX API key" in message
    assert "Do not guess" in message


def test_bus_tool_says_when_tdx_is_rate_limiting(monkeypatch: pytest.MonkeyPatch):
    use_fake_tdx(monkeypatch, httpx.Response(429, headers={"Retry-After": "6"}))

    message = bus_tool_error(route="132")

    assert "rate limiting" in message
    assert "6 seconds" in message
    assert "Do not guess" in message


def test_bus_tool_says_when_tdx_is_unusable(monkeypatch: pytest.MonkeyPatch):
    use_fake_tdx(monkeypatch, httpx.Response(200, text="<html>"))

    assert "could not be reached" in bus_tool_error(route="132")


def test_bus_tool_names_the_routes_for_an_unknown_one():
    assert "132, 133, 172, 173, 9025A" in bus_tool_error(route="1")


def test_bus_tool_description_forbids_guessing():
    tools = asyncio.run(mcp_server.mcp.list_tools())
    description = next(t.description for t in tools if t.name == "get_bus_arrivals")

    assert "Never guess" in description
    assert "rate" in description and "no TDX key" in description


def test_destination_tool_finds_off_campus_places():
    (answer,) = call("find_destination", query="airport")

    (airport,) = answer["destinations"]
    assert airport["id"] == "taoyuan-airport"
    assert airport["bus_routes"] == ["172", "173"]
    assert airport["getting_there"]["en"].startswith("Take bus 172 or 173")
    assert airport["maps_url"].startswith("https://www.google.com/maps/dir/")
    assert airport["checked_on"] == "2026-11-10"

    (stations,) = call("find_destination", category="station")
    assert [d["id"] for d in stations["destinations"]] == [
        "zhongli-train-station",
        "thsr-taoyuan-station",
    ]
    (nothing,) = call("find_destination", query="工程五館")
    assert nothing["destinations"] == []
