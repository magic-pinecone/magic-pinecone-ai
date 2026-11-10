"""A piece of text kept in both of the app's languages."""

from pydantic import BaseModel


class LocalizedName(BaseModel):
    zh: str
    en: str
