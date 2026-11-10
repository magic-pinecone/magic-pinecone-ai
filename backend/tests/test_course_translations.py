import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session

from app.models.catalog import CourseDetail
from app.services import course_translations
from app.services.course_translations import normalize_lang, translate
from tests.test_catalog import (  # noqa: F401
    DETAIL,
    client_fixture,
    ncu_fixture,
    session_fixture,
    synced_fixture,
)


def write_table(directory: Path, scope: str, lang: str, entries: dict[str, str]):
    (directory / f"{scope}.{lang}.json").write_text(
        json.dumps(
            {
                "source_language": "zh-Hant",
                "target_language": lang,
                "strategy": "lowLatency",
                "entries": entries,
            }
        ),
        encoding="utf-8",
    )


@pytest.fixture(name="tables")
def tables_fixture(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    write_table(
        tmp_path,
        "catalog",
        "en",
        {
            "國文": "Chinese",
            "文學院": "College of Liberal Arts",
            "中文系": "Chinese Literature",
            "學制:限學士班。": "Program: undergraduates only.",
        },
    )
    write_table(
        tmp_path,
        "detail",
        "en",
        {"學會讀": "Learn to read", "第一週": "Week 1", "講授": "Lecture"},
    )
    monkeypatch.setattr(course_translations, "DATA_DIR", tmp_path)
    course_translations.load_table.cache_clear()
    yield tmp_path
    course_translations.load_table.cache_clear()


@pytest.mark.parametrize(
    ("lang", "expected"),
    [
        ("en", "en"),
        ("en-US", "en"),
        ("VI-vn", "vi"),
        ("zh-TW", None),
        ("zh", None),
        ("ja", None),
        ("", None),
        (None, None),
    ],
)
def test_normalize_lang(lang: str | None, expected: str | None):
    assert normalize_lang(lang) == expected


def test_translate_keeps_untranslated_lines_and_whitespace():
    table = {"第一週": "Week 1"}
    assert translate("  第一週 \n  第二週 \n", table) == "Week 1\n  第二週 \n"


def test_missing_table_means_no_translation(tmp_path: Path, monkeypatch):
    monkeypatch.setattr(course_translations, "DATA_DIR", tmp_path)
    course_translations.load_table.cache_clear()
    assert course_translations.load_table("catalog", "vi") == {}
    course_translations.load_table.cache_clear()


def test_catalog_is_translated_when_asked(client: TestClient, synced, tables):
    first, sparse = client.get("/api/v1/catalog/115-1/courses?lang=en-US").json()[
        "courses"
    ]
    assert first["title"] == "Chinese"
    assert first["college_name"] == "College of Liberal Arts"
    assert first["department_name"] == "Chinese Literature"
    assert sparse["title"] == "專題"


@pytest.mark.parametrize("query", ["", "?lang=zh-TW", "?lang=vi", "?lang=fr"])
def test_catalog_stays_chinese_without_a_table(
    client: TestClient, synced, tables, query: str
):
    course = client.get(f"/api/v1/catalog/115-1/courses{query}").json()["courses"][0]
    assert course["title"] == "國文"


def test_invalid_lang_is_rejected(client: TestClient, synced):
    assert client.get("/api/v1/catalog/115-1/courses?lang=!!").status_code == 422


def test_detail_is_translated_line_by_line(
    client: TestClient, session: Session, synced, ncu, tables
):
    ncu.result = {**DETAIL, "content": "第一週\n第二週"}
    url = "/api/v1/catalog/115-1/courses/01001/detail"

    data = client.get(f"{url}?lang=en").json()
    assert data["objectives"] == "Learn to read"
    assert data["content"] == "Week 1\n第二週"
    assert data["teaching_method"] == "Lecture"
    assert data["grading_policy"] == "期末 100%"
    assert data["distribution_conditions"] == [
        {"priority": 1, "rule": "Program: undergraduates only."}
    ]

    # The cached syllabus stays Chinese, so the next request can pick any language.
    assert session.get(CourseDetail, 1001).objectives == "學會讀"
    assert client.get(url).json()["objectives"] == "學會讀"
