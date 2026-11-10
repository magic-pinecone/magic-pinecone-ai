"""Credit-rules endpoint for international students."""

from fastapi import APIRouter

from app.services import credits
from app.services.credits import Credits, Degree

router = APIRouter(prefix="/intl", tags=["intl"])


@router.get("/credits", response_model=Credits)
def get_credits(degree: Degree | None = None):
    """Return NCU's university-wide credit rules, optionally only those for one degree."""
    return credits.for_degree(degree)
