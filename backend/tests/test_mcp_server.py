import asyncio
import json

import pytest

from app import mcp_server


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
    }


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
