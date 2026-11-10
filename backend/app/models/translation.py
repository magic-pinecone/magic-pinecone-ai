from sqlmodel import Field, SQLModel


class Translation(SQLModel, table=True):
    """A cached machine translation, keyed by the source text's hash so edited text is translated again."""

    __tablename__ = "translations"

    lang: str = Field(primary_key=True)
    source_hash: str = Field(primary_key=True)  # sha256 hex of the source text
    text: str
