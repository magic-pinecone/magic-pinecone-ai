"""Service-learning endpoint for international students."""

from fastapi import APIRouter

from app.services import service_learning
from app.services.service_learning import Degree, ServiceLearning

router = APIRouter(prefix="/intl", tags=["intl"])


@router.get("/service-learning", response_model=ServiceLearning)
def get_service_learning(degree: Degree | None = None):
    """Return NCU's service-learning rules, optionally only those for one degree."""
    return service_learning.for_degree(degree)
