"""Credits (學分): NCU's university-wide credit rules and where each comes from.

The rules live in `app/data/credits/requirements.json`. Every rule quotes an
official ncu.edu.tw document that was read in full. Anything only seen in search
excerpts or pages that could not be opened is kept out of the rules and listed
in `open_questions` instead. Per-program graduation totals are in
`/intl/programs`, not here.
"""

import json
from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field

DATA_FILE = (
    Path(__file__).resolve().parent.parent / "data" / "credits" / "requirements.json"
)

Degree = Literal["bachelor", "graduate"]


class Source(BaseModel):
    url: str
    title: str
    fetched: str
    quote: str = Field(min_length=1)


class Contact(BaseModel):
    name_zh: str
    name_en: str
    website: str
    sources: list[Source] = Field(min_length=1)


class Requirement(BaseModel):
    id: str
    degrees: list[Degree] = Field(min_length=1)
    title_zh: str
    title_en: str
    summary_zh: str
    summary_en: str
    # The number of credits the rule is about, when it names one.
    credits: float | None = Field(default=None, ge=0)
    sources: list[Source] = Field(min_length=1)


class Credits(BaseModel):
    contact: Contact
    requirements: list[Requirement]
    open_questions: list[str]


@lru_cache
def load() -> Credits:
    data = Credits.model_validate(json.loads(DATA_FILE.read_text(encoding="utf-8")))
    ids = [requirement.id for requirement in data.requirements]
    if len(ids) != len(set(ids)):
        raise ValueError(f"Duplicate requirement ids in {DATA_FILE.name}")
    return data


def for_degree(degree: Degree | None = None) -> Credits:
    """The full rule set, keeping only the requirements for `degree` if given."""
    data = load()
    if degree is None:
        return data
    return data.model_copy(
        update={"requirements": [r for r in data.requirements if degree in r.degrees]}
    )
