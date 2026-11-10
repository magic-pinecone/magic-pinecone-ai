"""Service learning (服務學習): what NCU requires and where each rule comes from.

The rules live in `app/data/service_learning/requirements.json`. Every rule
quotes an official ncu.edu.tw document that was read in full. Anything only
seen in search excerpts or pages that could not be opened is kept out of the
rules and listed in `open_questions` instead.
"""

import json
from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field

DATA_FILE = (
    Path(__file__).resolve().parent.parent
    / "data"
    / "service_learning"
    / "requirements.json"
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
    sources: list[Source] = Field(min_length=1)


class ServiceLearning(BaseModel):
    contact: Contact
    requirements: list[Requirement]
    open_questions: list[str]


@lru_cache
def load() -> ServiceLearning:
    data = ServiceLearning.model_validate(
        json.loads(DATA_FILE.read_text(encoding="utf-8"))
    )
    ids = [requirement.id for requirement in data.requirements]
    if len(ids) != len(set(ids)):
        raise ValueError(f"Duplicate requirement ids in {DATA_FILE.name}")
    return data


def for_degree(degree: Degree | None = None) -> ServiceLearning:
    """The full rule set, keeping only the requirements for `degree` if given."""
    data = load()
    if degree is None:
        return data
    return data.model_copy(
        update={"requirements": [r for r in data.requirements if degree in r.degrees]}
    )
