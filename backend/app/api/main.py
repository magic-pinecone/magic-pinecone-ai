from fastapi import APIRouter

from app.api.routes import campus, catalog, courses, intl, scraper, transit
from app.core.config import settings

api_router = APIRouter()
api_router.include_router(campus.router)
api_router.include_router(catalog.router)
api_router.include_router(courses.router)
api_router.include_router(intl.router)
api_router.include_router(scraper.router)
api_router.include_router(transit.router)

if settings.ENVIRONMENT == "dev":
    # Dev-oriented routes
    pass
