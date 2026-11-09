from fastapi import APIRouter

from app.api.routes import announcements, catalog, courses, scraper
from app.core.config import settings

api_router = APIRouter()
api_router.include_router(announcements.router)
api_router.include_router(catalog.router)
api_router.include_router(courses.router)
api_router.include_router(scraper.router)

if settings.ENVIRONMENT == "dev":
    # Dev-oriented routes
    pass
