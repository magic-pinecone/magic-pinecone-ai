"""Integrity of ml/golden/questions.jsonl against the corpus the backend loads."""

from collections import Counter

import pytest
from common import expected_sources, load_golden, student_services

GOLDEN = load_golden()
BY_ID = {item["id"]: item for item in GOLDEN}
DOCUMENTS = {
    document.id: "\n".join(document.lines)
    for document in student_services().load_documents()
}
REQUIRED = {
    "id": str,
    "lang": str,
    "question": str,
    "answerable": bool,
    "must_contain": list,
    "needs_native_review": bool,
}
OPTIONAL = {"source_id", "alt_source_ids", "parallel_of"}


def test_size_and_unique_ids():
    assert 55 <= len(GOLDEN) <= 65
    assert len(BY_ID) == len(GOLDEN)
    questions = [item["question"] for item in GOLDEN]
    assert len(set(questions)) == len(questions)


@pytest.mark.parametrize("item", GOLDEN, ids=lambda item: item["id"])
def test_schema(item):
    assert set(item) <= set(REQUIRED) | OPTIONAL
    for field, kind in REQUIRED.items():
        assert isinstance(item[field], kind), field
    assert item["lang"] in ("en", "zh-TW", "vi", "id")
    assert item["question"].strip() == item["question"] != ""
    assert item["needs_native_review"] == (item["lang"] in ("vi", "id"))
    if item["answerable"]:
        assert item["source_id"] in DOCUMENTS
        assert item["must_contain"]
        assert all(isinstance(s, str) and s for s in item["must_contain"])
    else:
        assert item["source_id"] is None
        assert item["must_contain"] == []
        assert "alt_source_ids" not in item


@pytest.mark.parametrize(
    "item", [i for i in GOLDEN if i["answerable"]], ids=lambda item: item["id"]
)
def test_must_contain_is_verbatim_in_every_expected_source(item):
    for source in expected_sources(item):
        assert source in DOCUMENTS
        for fact in item["must_contain"]:
            assert fact in DOCUMENTS[source], (source, fact)


@pytest.mark.parametrize(
    "item", [i for i in GOLDEN if "parallel_of" in i], ids=lambda item: item["id"]
)
def test_parallel_items_mirror_their_english_original(item):
    original = BY_ID[item["parallel_of"]]
    assert original["lang"] == "en" and item["lang"] != "en"
    for field in ("answerable", "source_id", "must_contain"):
        assert item[field] == original[field]
    assert item.get("alt_source_ids") == original.get("alt_source_ids")


def test_language_and_answerability_ratios():
    share = {
        lang: count / len(GOLDEN)
        for lang, count in Counter(item["lang"] for item in GOLDEN).items()
    }
    assert 0.35 <= share["en"] <= 0.45
    for lang in ("zh-TW", "vi", "id"):
        assert 0.15 <= share[lang] <= 0.25
    unanswerable = [item for item in GOLDEN if not item["answerable"]]
    assert 0.10 <= len(unanswerable) / len(GOLDEN) <= 0.20
    assert {item["lang"] for item in unanswerable} == set(share)


def test_most_non_english_items_are_parallel():
    others = [item for item in GOLDEN if item["lang"] != "en"]
    assert sum("parallel_of" in item for item in others) >= 0.75 * len(others)
