"""Fetch the official pages behind the graduation language requirements.

Reads `app/data/graduation_language/sources.json`, downloads every source that
has a `url`, extracts its text, and writes one Markdown file per source to
`app/data/graduation_language/corpus/`. Those files are the retrieval corpus;
`requirements.json` in the same folder is curated by hand from them.

    uv run python scripts/build_graduation_language_corpus.py          # rebuild
    uv run python scripts/build_graduation_language_corpus.py --check  # report drift

`--check` downloads everything but writes nothing, and exits 1 when a source
changed or could not be fetched, so a scheduled job can flag that the curated
requirements need a second look.

PDF sources need `pdftotext` (poppler-utils) on PATH and Word sources
(`"extract": "office"`) need `soffice` (LibreOffice). Sources with
`"extract": "manual"` (images, scanned files) are never overwritten: their
corpus file is transcribed by hand and only the hash of the original is tracked.
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import time
from datetime import date
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit

import bs4
import httpx

DATA_DIR = Path(__file__).resolve().parent.parent / "app/data/graduation_language"
SOURCES_FILE = DATA_DIR / "sources.json"
CORPUS_DIR = DATA_DIR / "corpus"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "zh-TW,en;q=0.8",
}
# NCU hosts drop connections now and then; a short retry clears it.
ATTEMPTS = 4
PAUSE_SECONDS = 1.0
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)


def encode_url(url: str) -> str:
    """Percent-encode the non-ASCII path NCU uses in file links."""
    parts = urlsplit(url)
    return urlunsplit(
        parts._replace(
            path=quote(parts.path, safe="/%()"), query=quote(parts.query, safe="=&%")
        )
    )


def fetch(client: httpx.Client, url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(ATTEMPTS):
        try:
            response = client.get(encode_url(url))
            response.raise_for_status()
            return response.content
        except httpx.HTTPError as error:
            last_error = error
            time.sleep(PAUSE_SECONDS * (attempt + 1))
    raise RuntimeError(f"{url}: {last_error}")


def html_to_text(raw: bytes, selector: str | None) -> str:
    soup = bs4.BeautifulSoup(raw, "html.parser")
    for tag in soup(["script", "style", "nav", "header", "footer", "noscript"]):
        tag.decompose()
    root = (soup.select_one(selector) if selector else None) or soup.body or soup
    for cell in root.find_all(["td", "th"]):
        cell.append(" | ")
    lines = [line.strip() for line in root.get_text("\n").split("\n")]
    return "\n".join(line for line in lines if line and line != "|")


def pdf_to_text(raw: bytes) -> str:
    result = subprocess.run(
        ["pdftotext", "-layout", "-", "-"], input=raw, capture_output=True, check=True
    )
    lines = [
        re.sub(r"[ \t]{2,}", "  ", line).rstrip()
        for line in result.stdout.decode().split("\n")
    ]
    return "\n".join(line for line in lines if line.strip())


def office_to_text(raw: bytes) -> str:
    """Convert a Word file (.doc, .docx, .odt) with LibreOffice."""
    with tempfile.TemporaryDirectory() as folder:
        source = Path(folder) / "source"
        source.write_bytes(raw)
        subprocess.run(
            ["soffice", "--headless", "--convert-to", "txt:Text (encoded):UTF8"]
            + ["--outdir", folder, str(source)],
            capture_output=True,
            check=True,
            timeout=180,
        )
        text = (Path(folder) / "source.txt").read_text(encoding="utf-8-sig")
    lines = [line.strip() for line in text.split("\n")]
    return "\n".join(line for line in lines if line)


def extract(source: dict, raw: bytes) -> str:
    kind = source.get("extract", "html")
    if kind == "pdf":
        return pdf_to_text(raw)
    if kind == "office":
        return office_to_text(raw)
    return html_to_text(raw, source.get("selector"))


def read_front_matter(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    match = FRONT_MATTER.match(path.read_text(encoding="utf-8"))
    if not match:
        return {}
    pairs = (line.split(": ", 1) for line in match.group(1).split("\n") if ": " in line)
    return {key: json.loads(value) for key, value in pairs}


def render(source: dict, sha256: str, fetched_on: str, body: str) -> str:
    meta = {
        "id": source["id"],
        "title": source["title"],
        "url": source["url"],
        "org": source["org"],
        "lang": source["lang"],
        "degree_levels": source["degree_levels"],
        "effective": source.get("effective", ""),
        "extract": source.get("extract", "html"),
        "fetched_on": fetched_on,
        "sha256": sha256,
    }
    head = "\n".join(
        f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in meta.items()
    )
    return f"---\n{head}\n---\n\n# {source['title']}\n\n{body.strip()}\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument(
        "--check", action="store_true", help="report drift, write nothing"
    )
    parser.add_argument("--only", nargs="*", help="source ids to process")
    args = parser.parse_args()

    sources = json.loads(SOURCES_FILE.read_text(encoding="utf-8"))["sources"]
    CORPUS_DIR.mkdir(exist_ok=True)
    problems = 0
    today = date.today().isoformat()

    with httpx.Client(headers=HEADERS, timeout=60, follow_redirects=True) as client:
        for source in sources:
            if args.only and source["id"] not in args.only:
                continue
            path = CORPUS_DIR / f"{source['id']}.md"
            previous = read_front_matter(path)
            try:
                raw = fetch(client, source["url"])
            except RuntimeError as error:
                print(f"FAILED   {source['id']}: {error}")
                problems += 1
                continue
            time.sleep(PAUSE_SECONDS)
            sha256 = hashlib.sha256(raw).hexdigest()
            manual = source.get("extract") == "manual"

            if previous.get("sha256") == sha256:
                print(f"same     {source['id']}")
                continue
            if previous:
                status = "CHANGED"
                problems += 1
            else:
                status = "new"
                problems += manual  # nobody has transcribed it yet
            hint = " (transcribe by hand, then set sha256)" if manual else ""
            print(f"{status:8} {source['id']}{hint}  sha256={sha256}")
            if args.check or manual:
                continue
            path.write_text(
                render(source, sha256, today, extract(source, raw)), encoding="utf-8"
            )

    return 1 if args.check and problems else 0


if __name__ == "__main__":
    sys.exit(main())
