from sqlmodel import Session, SQLModel, create_engine

from app.core.config import settings
from app.models import (  # noqa: F401
    Announcement,
    AnnouncementSyncState,
    CatalogState,
    College,
    Course,
    CourseDetail,
    Department,
)

engine = create_engine(str(settings.SQLALCHEMY_DATABASE_URI))


def init_db(session: Session):
    SQLModel.metadata.create_all(engine)
