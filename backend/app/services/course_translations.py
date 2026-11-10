"""Course text in English and Vietnamese, from the tables in `app/data/course_translations/`.

Each file `{scope}.{lang}.json` maps one trimmed Chinese source line to its
translation. `catalog` covers the course list (titles, college and department
names, distribution rules) and `detail` the syllabus fields. A missing file or
line leaves the Chinese text as it is.
"""

import json
from functools import lru_cache
from pathlib import Path
from typing import Literal

DATA_DIR = Path(__file__).resolve().parent.parent / "data/course_translations"
SUPPORTED_LANGS = ("en", "vi")

Scope = Literal["catalog", "detail"]
Table = dict[str, str]


def normalize_lang(lang: str | None) -> str | None:
    """Reduce a language tag to a supported table language, or None for no translation."""
    base = (lang or "").lower().split("-")[0]
    return base if base in SUPPORTED_LANGS else None


@lru_cache
def load_table(scope: Scope, lang: str) -> Table:
    path = DATA_DIR / f"{scope}.{lang}.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))["entries"]


def translate(text: str, table: Table) -> str:
    """Translate line by line; a line with no entry keeps its original text."""
    return "\n".join(table.get(line.strip(), line) for line in text.split("\n"))
