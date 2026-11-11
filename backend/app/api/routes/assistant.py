"""Assistant endpoint: one question in, what the backend knows about it out."""

from datetime import datetime
from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services import assistant
from app.services.campus_places import CampusPlace
from app.services.destinations import DestinationResponse
from app.services.tdx import StopArrival

router = APIRouter(prefix="/assistant", tags=["assistant"])


class Question(BaseModel):
    question: str = Field(min_length=1, max_length=assistant.MAX_QUESTION_CHARS)


class Passage(BaseModel):
    id: str
    title: str
    url: str
    # When the page was read, or the facts were checked. None for a building.
    fetched_on: str | None
    passage: str


class RouteArrivals(BaseModel):
    route: str
    fetched_at: datetime
    source: str = assistant.BUS_SOURCE
    stops: list[StopArrival]


class AnswerChecks(BaseModel):
    # The model said the passages do not hold the answer.
    refused: bool
    # Numbers of the passages the answer cites, counted from 1.
    cited: list[int]
    citations_valid: bool
    # Numbers in the answer that no passage contains.
    unsupported_numbers: list[str]


class AskResponse(BaseModel):
    question: str
    kind: assistant.Kind
    # What was searched: the question, or its English translation.
    search_query: str
    translated: bool
    # What the model was given to answer from, in the order it cites them.
    passages: list[Passage]
    places: list[CampusPlace]
    destinations: list[DestinationResponse]
    bus: list[RouteArrivals]
    # Why a bus question has no live arrivals; never a guess instead.
    bus_problem: assistant.BusProblem | None
    # None when this server has no model.
    model: str | None
    answer: str | None
    checks: AnswerChecks | None
    error: str | None
    seconds: float | None


@router.post("/ask", response_model=AskResponse)
def ask(body: Question) -> Any:  # a sync route: the model call blocks
    """Look a student's question up and, when a model is configured, answer it.

    A question naming a bus route or an off-campus destination gets live bus
    arrivals, one naming a building gets that building, and anything else gets
    passages of the OIA pages. Without a model `answer` is null and the rest
    is still filled in.
    """
    return assistant.ask(body.question, assistant.configured_model())
