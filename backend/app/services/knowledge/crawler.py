import argparse
import hashlib
import io
import json
import logging
import re
import time
import urllib.robotparser
from collections import deque
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urljoin, urlsplit, urlunsplit

import httpx
from pypdf import PdfReader
from selectolax.parser import HTMLParser, Node
from tenacity import retry, stop_after_attempt, wait_exponential

logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data" / "oia_site"
SITE = "https://oia.ncu.edu.tw"
HOST = "oia.ncu.edu.tw"
START_URLS = (f"{SITE}/index.php?Lang=en", f"{SITE}/index.php?Lang=zh-tw")
ORG = "Office of International Affairs"
USER_AGENT = (
    "MagicPineconeBot/1.0 (+https://github.com/magic-pinecone/magic-pinecone-ai)"
)
DELAY_SECONDS = 1.0
MAX_PDF_BYTES = 10 * 1024 * 1024
MAX_PDF_PAGES = 20
MIN_TEXT_CHARS = 80
CONTENT_SELECTORS = (".mpgdetail", ".mcontent", "#main")
TITLE_SELECTORS = ("h2.hdline", ".mpgtitle", "h1")
TITLE_SUFFIX = re.compile(
    r"\s*[-|｜]\s*(Office of International Affairs|國際事務處).*$"
)
BLOCK_TAGS = frozenset(
    "p div li tr h1 h2 h3 h4 h5 h6 table ul ol section article dd dt blockquote "
    "caption figcaption header footer".split()
)
SKIP_TAGS = frozenset("script style noscript iframe form button select".split())
PAGE_PATH = re.compile(r"^/(p/\d+-\d+-\d+(-\d+)?\.php|index\.php)?$")
FILE_PATH = re.compile(r"^/static/file/.+\.pdf$", re.IGNORECASE)
LANG_LABEL = {"en": "en", "zh-tw": "zh-TW"}


def normalize(url: str, base: str = SITE) -> str | None:
    parts = urlsplit(urljoin(base, url.strip()))
    if parts.scheme not in ("http", "https") or parts.hostname != HOST:
        return None
    path = parts.path or "/"
    if FILE_PATH.match(path):
        return urlunsplit(("https", HOST, path, "", ""))
    if not PAGE_PATH.match(path):
        return None
    lang = parse_qs(parts.query).get("Lang", [""])[0].casefold()
    if path == "/":
        path = "/index.php"
    query = urlencode({"Lang": lang}) if lang in LANG_LABEL else ""
    return urlunsplit(("https", HOST, path, query, ""))


def page_lang(url: str) -> str:
    lang = parse_qs(urlsplit(url).query).get("Lang", ["zh-tw"])[0].casefold()
    return LANG_LABEL.get(lang, "zh-TW")


def document_id(url: str) -> str:
    return "oia-site-" + hashlib.sha256(url.encode()).hexdigest()[:12]


def _clean(text: str) -> str:
    return re.sub(r"[ \t 　]+", " ", text).strip()


def node_lines(root: Node, base: str) -> list[str]:
    lines: list[str] = []
    buffer: list[str] = []

    def flush() -> None:
        line = _clean("".join(buffer)).strip(" |")
        buffer.clear()
        if line:
            lines.append(line)

    def walk(node: Node) -> None:
        for child in node.iter(include_text=True):
            tag = child.tag
            if tag == "-text":
                buffer.append(child.text_content or "")
            elif tag in SKIP_TAGS:
                continue
            elif tag == "br":
                flush()
            elif tag in ("td", "th"):
                walk(child)
                buffer.append(" | ")
            elif tag == "a":
                label = _clean(child.text(separator=" "))
                href = child.attributes.get("href") or ""
                target = urljoin(base, href) if href else ""
                flush()
                if label:
                    lines.append(label)
                if target.startswith("http"):
                    lines.append(f"<{target}>")
            elif tag in BLOCK_TAGS:
                flush()
                walk(child)
                flush()
            else:
                walk(child)

    walk(root)
    flush()
    return [
        line
        for index, line in enumerate(lines)
        if index == 0 or line != lines[index - 1]
    ]


def page_title(tree: HTMLParser) -> str:
    for selector in TITLE_SELECTORS:
        node = tree.css_first(selector)
        if node and _clean(node.text()):
            return _clean(node.text())
    node = tree.css_first("title")
    return TITLE_SUFFIX.sub("", _clean(node.text())) if node else ""


def parse_page(html: str, url: str) -> tuple[str, list[str], list[str]]:
    tree = HTMLParser(html)
    links = [
        link
        for node in tree.css("a[href]")
        if (link := normalize(node.attributes.get("href") or "", url))
    ]
    content = next(
        (node for selector in CONTENT_SELECTORS if (node := tree.css_first(selector))),
        None,
    )
    lines = node_lines(content, url) if content else []
    return page_title(tree), lines, links


def parse_pdf(data: bytes) -> list[str]:
    reader = PdfReader(io.BytesIO(data))
    lines: list[str] = []
    for page in reader.pages[:MAX_PDF_PAGES]:
        text = page.extract_text() or ""
        lines += [line for raw in text.splitlines() if (line := _clean(raw))]
    return lines


def render(source: dict, lines: list[str]) -> str:
    head = "\n".join(
        f"{key}: {json.dumps(value, ensure_ascii=False)}"
        for key, value in source.items()
    )
    body = "\n".join(lines)
    return f"---\n{head}\n---\n\n# {source['title']}\n\n{body}\n"


class Crawler:
    def __init__(
        self, client: httpx.Client, max_pages: int, delay: float = DELAY_SECONDS
    ):
        self.client = client
        self.max_pages = max_pages
        self.delay = delay
        self.robots = urllib.robotparser.RobotFileParser()
        self._last = 0.0

    def _wait(self) -> None:
        pause = self.delay - (time.monotonic() - self._last)
        if pause > 0:
            time.sleep(pause)
        self._last = time.monotonic()

    @retry(
        stop=stop_after_attempt(3), wait=wait_exponential(min=2, max=20), reraise=True
    )
    def fetch(self, url: str) -> httpx.Response:
        self._wait()
        response = self.client.get(url)
        if response.status_code >= 500:
            response.raise_for_status()
        return response

    def load_robots(self) -> None:
        try:
            response = self.fetch(f"{SITE}/robots.txt")
            self.robots.parse(
                response.text.splitlines() if response.status_code == 200 else []
            )
        except httpx.HTTPError:
            self.robots.parse([])

    def crawl(self) -> list[tuple[dict, list[str]]]:
        self.load_robots()
        queue = deque(START_URLS)
        seen = set(START_URLS)
        hashes: set[str] = set()
        found: list[tuple[dict, list[str]]] = []
        visited = 0
        titles: dict[str, str] = {}
        while queue and visited < self.max_pages:
            url = queue.popleft()
            if not self.robots.can_fetch(USER_AGENT, url):
                continue
            visited += 1
            try:
                response = self.fetch(url)
            except httpx.HTTPError as error:
                logger.warning("skip %s: %s", url, error)
                continue
            if response.status_code != 200:
                continue
            kind = response.headers.get("content-type", "")
            if "pdf" in kind or FILE_PATH.match(urlsplit(url).path):
                if len(response.content) > MAX_PDF_BYTES:
                    continue
                try:
                    lines = parse_pdf(response.content)
                except Exception as error:
                    logger.warning("unreadable pdf %s: %s", url, error)
                    continue
                title = titles.get(url) or Path(urlsplit(url).path).stem
                extract, lang = "pdf", ""
            else:
                title, lines, links = parse_page(response.text, url)
                extract, lang = "html", page_lang(url)
                for link in links:
                    if link not in seen:
                        seen.add(link)
                        queue.append(link)
                tree = HTMLParser(response.text)
                for node in tree.css("a[href]"):
                    link = normalize(node.attributes.get("href") or "", url)
                    label = _clean(node.text())
                    if link and FILE_PATH.match(urlsplit(link).path) and label:
                        titles.setdefault(link, label)
            text = "\n".join(lines)
            if len(text) < MIN_TEXT_CHARS:
                continue
            digest = hashlib.sha256(text.encode()).hexdigest()
            if digest in hashes:
                continue
            hashes.add(digest)
            source = {
                "id": document_id(url),
                "title": title or url,
                "url": url,
                "org": ORG,
                "lang": lang or detect_lang(text),
                "topic": "oia_site",
                "extract": extract,
                "fetched_on": datetime.now(UTC).date().isoformat(),
                "sha256": digest,
            }
            found.append((source, lines))
            logger.info("%d/%d %s", visited, self.max_pages, url)
        return found


def detect_lang(text: str) -> str:
    chinese = len(re.findall(r"[一-鿿]", text))
    return "zh-TW" if chinese > len(text) * 0.15 else "en"


def write(found: list[tuple[dict, list[str]]], data_dir: Path = DATA_DIR) -> None:
    corpus = data_dir / "corpus"
    corpus.mkdir(parents=True, exist_ok=True)
    keep = set()
    for source, lines in found:
        path = corpus / f"{source['id']}.md"
        keep.add(path.name)
        path.write_text(render(source, lines), encoding="utf-8")
    for stale in corpus.glob("*.md"):
        if stale.name not in keep:
            stale.unlink()
    sources = [
        {
            key: value
            for key, value in source.items()
            if key not in ("fetched_on", "sha256")
        }
        for source, _ in sorted(found, key=lambda item: item[0]["url"])
    ]
    (data_dir / "sources.json").write_text(
        json.dumps({"sources": sources}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def run(max_pages: int, delay: float = DELAY_SECONDS) -> int:
    with httpx.Client(
        headers={"User-Agent": USER_AGENT}, timeout=30.0, follow_redirects=True
    ) as client:
        found = Crawler(client, max_pages=max_pages, delay=delay).crawl()
    if found:
        write(found)
    return len(found)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-pages", type=int, default=800)
    parser.add_argument("--delay", type=float, default=DELAY_SECONDS)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    print(f"{run(args.max_pages, args.delay)} documents written to {DATA_DIR}")


if __name__ == "__main__":
    main()
