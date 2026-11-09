from fastapi import APIRouter

from app.api.routes import catalog, clubs, courses, scraper
from app.core.config import settings

api_router = APIRouter()
api_router.include_router(catalog.router)
api_router.include_router(clubs.router)
api_router.include_router(courses.router)
api_router.include_router(scraper.router)

if settings.ENVIRONMENT == "dev":
    # Dev-oriented routes
    pass
