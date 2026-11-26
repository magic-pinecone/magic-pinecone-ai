import asyncio
import logging
from contextlib import AsyncExitStack, asynccontextmanager

from fastapi import FastAPI
from fastapi.routing import APIRoute
from mcp.server.transport_security import TransportSecuritySettings
from sqlmodel import Session
from starlette.middleware.cors import CORSMiddleware

from app.api.main import api_router
from app.core.config import settings
from app.core.db import engine, init_db
from app.mcp_server import mcp
from app.services.knowledge import index as knowledge_index
from app.tasks.club_scraper import queue_first_club_sync

logger = logging.getLogger(__name__)


def refresh_knowledge() -> None:
    with knowledge_index.REINDEX_LOCK:
        knowledge_index.refresh_from_database()


async def refresh_knowledge_periodically() -> None:
    while True:
        try:
            await asyncio.to_thread(refresh_knowledge)
        except Exception:
            logger.exception("knowledge refresh failed")
        if settings.KNOWLEDGE_REFRESH_MINUTES <= 0:
            return
        await asyncio.sleep(settings.KNOWLEDGE_REFRESH_MINUTES * 60)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize DB tables on startup
    with Session(engine) as session:
        init_db(session)
        queue_first_club_sync(session)
    refresher = asyncio.create_task(refresh_knowledge_periodically())
    async with AsyncExitStack() as stack:
        if settings.MCP_HTTP_ENABLED:
            await stack.enter_async_context(mcp.session_manager.run())
        yield
    refresher.cancel()


def custom_generate_unique_id(route: APIRoute):
    return f"{route.tags[0] if route.tags else 'default'}-{route.name}"


app = FastAPI(
    title=settings.PROJECT_NAME,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    generate_unique_id_function=custom_generate_unique_id,
    lifespan=lifespan,
)

if settings.all_cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.all_cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(api_router, prefix=settings.API_V1_STR)

if settings.MCP_HTTP_ENABLED:
    app.mount(
        "/",
        mcp.streamable_http_app(
            streamable_http_path="/mcp",
            stateless_http=True,
            json_response=True,
            host="0.0.0.0",
            transport_security=TransportSecuritySettings(
                enable_dns_rebinding_protection=True,
                allowed_hosts=settings.MCP_ALLOWED_HOSTS,
            )
            if settings.MCP_ALLOWED_HOSTS
            else None,
        ),
    )
