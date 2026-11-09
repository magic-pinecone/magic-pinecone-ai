"""Graduation language requirements, curated from NCU's official pages.

The numbers live in `app/data/graduation_language/requirements.json`. They are
transcribed by hand from the sources listed in `sources.json`, because NCU
publishes them as images and PDF forms. Nothing here is generated: a requirement
the sources do not state is reported as unknown, never guessed.
"""

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

DATA_DIR = Path(__file__).resolve().parent.parent / "data/graduation_language"


@lru_cache
def load_requirements() -> dict[str, Any]:
    return json.loads((DATA_DIR / "requirements.json").read_text(encoding="utf-8"))


@lru_cache
def load_sources() -> list[dict[str, Any]]:
    data = json.loads((DATA_DIR / "sources.json").read_text(encoding="utf-8"))
    return data["sources"]


@lru_cache
def load_programs() -> dict[str, Any]:
    return json.loads((DATA_DIR / "programs.json").read_text(encoding="utf-8"))


def find_programs(
    degree: str | None = None,
    teaching_language: str | None = None,
    query: str | None = None,
) -> list[dict[str, Any]]:
    """Programs open to international students. `query` matches names and college."""
    programs = load_programs()["programs"]
    if degree:
        programs = [p for p in programs if p["degree"] == degree]
    if teaching_language:
        programs = [p for p in programs if p["teaching_language"] == teaching_language]
    if query:
        wanted = query.strip().casefold()
        programs = [
            p
            for p in programs
            if any(
                wanted in text.casefold()
                for text in (*p["name"].values(), *p["college"].values())
            )
        ]
    return programs


def applies_to_cohort(threshold: dict[str, Any], cohort: int) -> bool:
    """Whether a threshold covers students admitted in academic year `cohort`."""
    return (
        threshold.get("cohort_min", cohort)
        <= cohort
        <= threshold.get("cohort_max", cohort)
    )


def for_cohort(rule: dict[str, Any], cohort: int) -> dict[str, Any]:
    """The rule with each test narrowed to the thresholds for one admission year."""
    english = {
        test: [t for t in thresholds if applies_to_cohort(t, cohort)]
        for test, thresholds in rule.get("english", {}).items()
    }
    return {**rule, "english": {test: ts for test, ts in english.items() if ts}}


def find_rules(
    degree: str | None = None, college: str | None = None, cohort: int | None = None
) -> list[dict[str, Any]]:
    """Rules matching the filters. `college` matches the rule id or either name."""
    rules = load_requirements()["rules"]
    if degree:
        rules = [r for r in rules if r["degree"] == degree]
    if college:
        wanted = college.strip().casefold()
        rules = [
            r
            for r in rules
            if wanted
            in {r["id"].casefold(), *(n.casefold() for n in r["college"].values())}
        ]
    if cohort is not None:
        rules = [for_cohort(r, cohort) for r in rules]
    return rules
