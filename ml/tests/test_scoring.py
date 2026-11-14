from eval_generation import comparison_markdown, score_item, summarize
from eval_retrieval import evaluate
from prompt_format import REFUSALS

ITEM = {
    "id": "en-x",
    "lang": "en",
    "question": "How many hours per week can I work in Fall 2026?",
    "answerable": True,
    "source_id": "oia-work-permit",
    "must_contain": ["20"],
}
UNANSWERABLE = {
    **ITEM,
    "id": "en-u",
    "answerable": False,
    "source_id": None,
    "must_contain": [],
}
GOLD = {
    "id": "oia-work-permit",
    "title": "Work permit",
    "passage": "the maximum working hours are 20 hours per week.",
}
OTHER = {
    "id": "oia-insurance",
    "title": "Insurance",
    "passage": "NT$1,000\nper day/visit.",
}


def test_correct_answer_with_correct_citation():
    s = score_item(ITEM, [OTHER, GOLD], "- At most 20 hours per week in Fall 2026 [2]")
    assert s["answer_ok"] and s["citation_ok"] and s["ctx_has_answer"]
    assert not s["refused"] and s["unsupported_numbers"] == []


def test_wrong_citations():
    assert not score_item(ITEM, [OTHER, GOLD], "- 20 hours [1]")["citation_ok"]
    assert not score_item(ITEM, [OTHER, GOLD], "- 20 hours [3]")["citation_ok"]
    assert not score_item(ITEM, [OTHER, GOLD], "- 20 hours")["citation_ok"]
    assert score_item(ITEM, [OTHER, GOLD], "- 20 hours [1][2]")["citation_ok"]
    alt = {**ITEM, "alt_source_ids": ["oia-insurance"]}
    assert score_item(alt, [OTHER, GOLD], "- 20 hours [1]")["citation_ok"]


def test_wrong_number_is_an_answer_and_a_number_error():
    s = score_item(ITEM, [OTHER, GOLD], "- At most 25 hours per week [2]")
    assert not s["answer_ok"] and s["unsupported_numbers"] == ["25"]
    # 2026 is not flagged above because the question mentions it; 2027 is.
    assert score_item(ITEM, [GOLD], "- 20 hours in 2027 [1]")[
        "unsupported_numbers"
    ] == ["2027"]


def test_refusal_scoring():
    refused = score_item(UNANSWERABLE, [OTHER], REFUSALS["en"])
    assert refused["refused"] and refused["unsupported_numbers"] == []
    assert not refused["answer_ok"] and not refused["citation_ok"]
    over = score_item(ITEM, [GOLD], REFUSALS["zh-TW"])
    assert over["refused"] and over["ctx_has_answer"] and not over["answer_ok"]
    no_ctx = score_item(ITEM, [], REFUSALS["en"])
    assert no_ctx["refused"] and not no_ctx["ctx_has_answer"]


def test_summarize_and_comparison_table():
    def record(item, passages, response, lang="en"):
        return {
            "lang": lang,
            "answerable": item["answerable"],
            **score_item(item, passages, response),
        }

    records = [
        record(ITEM, [GOLD], "- 20 hours [1]"),
        record(ITEM, [GOLD], REFUSALS["en"]),
        record(ITEM, [OTHER], REFUSALS["en"]),
        record(ITEM, [OTHER], "- 99 hours [1]", lang="vi"),
        record(UNANSWERABLE, [OTHER], REFUSALS["en"]),
        record(UNANSWERABLE, [OTHER], "- NT$1,000 [1]"),
    ]
    summary = summarize(records)
    en, everything = summary["en"], summary["all"]
    assert (en["answerable"], en["unanswerable"], en["ctx_has_answer"]) == (3, 2, 2)
    assert (en["answer_ok"], en["answer_ok_ctx"], en["citation_ok"]) == (1, 1, 1)
    assert (en["refused_unanswerable"], en["refused_no_ctx"], en["over_refused"]) == (
        1,
        1,
        1,
    )
    assert en["number_error"] == 0 and summary["vi"]["number_error"] == 1
    assert everything["total"] == 6 and summary["id"]["total"] == 0
    table = comparison_markdown(
        [
            {"run_name": "b", "passages": "retrieved", "summary": summary},
            {"run_name": "a", "passages": "retrieved", "summary": summary},
        ]
    )
    lines = table.splitlines()
    header = next(line for line in lines if line.startswith("| run"))
    rows = [line for line in lines if line.startswith(("| a ", "| b "))]
    assert len(rows) == 10 and rows[0].startswith("| a ")
    assert all(line.count("|") == header.count("|") for line in rows)


def test_retrieval_metrics():
    def search(question, limit):
        if "nothing" in question:
            return []
        return [
            {"id": "oia-insurance", "passage": OTHER["passage"]},
            {"id": "oia-work-permit", "passage": GOLD["passage"]},
        ][:limit]

    golden = [ITEM, {**ITEM, "id": "en-n", "question": "nothing"}, UNANSWERABLE]
    report = evaluate(golden, search)
    s = report["summary"]["en"]
    assert (s["answerable"], s["hit1"], s["hit3"], s["ans3"], s["no_result"]) == (
        2,
        0,
        1,
        1,
        1,
    )
    assert (s["unanswerable"], s["unanswerable_with_passages"]) == (1, 1)
    alt = evaluate([{**ITEM, "alt_source_ids": ["oia-insurance"]}], search)
    assert alt["summary"]["all"]["hit1"] == 1


def test_a_number_inside_a_file_name_is_not_the_answer_in_the_passages():
    link = {**GOLD, "passage": "<https://oia.ncu.edu.tw/img/389/std20230311.pdf>"}
    s = score_item(ITEM, [link], REFUSALS["en"])
    assert s["refused"] and not s["ctx_has_answer"]
    counts = summarize([{**ITEM, **s}])["en"]
    assert counts["over_refused"] == 0 and counts["refused_no_ctx"] == 1


ANY_OF = {**ITEM, "must_contain": [], "must_contain_any": ["30", "三十"]}
DAYS = {"id": "oia-insurance", "title": "Insurance", "passage": "less than 30 days"}


def test_must_contain_any_takes_one_spelling():
    for answer in ("- 最多三十天 [1]", "- 少於 30 天 [1]"):
        s = score_item(ANY_OF, [DAYS], answer)
        assert s["answer_ok"] and s["ctx_has_answer"]
    assert not score_item(ANY_OF, [DAYS], "- 最多 2030 天 [1]")["answer_ok"]
    assert not score_item(ANY_OF, [OTHER], "- 三十天 [1]")["ctx_has_answer"]


def test_must_contain_any_is_asked_for_besides_must_contain():
    both = {**ANY_OF, "must_contain": ["once"]}
    passage = {**DAYS, "passage": "only once, less than 30 days"}
    assert score_item(both, [passage], "- once, 三十天 [1]")["answer_ok"]
    assert not score_item(both, [passage], "- 三十天 [1]")["answer_ok"]
    assert not score_item(both, [passage], "- only once [1]")["answer_ok"]
