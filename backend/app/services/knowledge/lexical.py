import math
import re
import unicodedata
from collections import Counter
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

STOPWORDS = frozenset(
    "a an and are as at be can do does for how i in is it my of on or the to "
    "what when where which who will with you your".split()
)
TOKEN = re.compile(r"[一-鿿]+|[^\W_一-鿿]+")
CHINESE = re.compile(r"[一-鿿]")


def tokens(text: str) -> list[str]:
    return TOKEN.findall(unicodedata.normalize("NFC", text).casefold())


def stem(word: str) -> str:
    plural = len(word) > 3 and word.endswith("s") and not word.endswith("ss")
    return word[:-1] if plural and word.isascii() and word.isalpha() else word


def words(text: str) -> list[str]:
    found = []
    for word in tokens(text):
        if len(word) > 2 and CHINESE.match(word):
            found += [word[i : i + 2] for i in range(len(word) - 1)]
        else:
            found.append(word)
    return [word for word in dict.fromkeys(found) if word not in STOPWORDS]


@dataclass(frozen=True)
class Chunk:
    document: Any
    start: int
    text: str
    folded: str
    stems: Counter


def make_chunk(document: Any, start: int, text: str) -> Chunk:
    titled = unicodedata.normalize("NFC", f"{document.title}\n{text}")
    return Chunk(
        document=document,
        start=start,
        text=text,
        folded=titled.casefold(),
        stems=Counter(stem(token) for token in tokens(titled)),
    )


def windows(lines: tuple[str, ...], size: int, step: int) -> list[tuple[int, str]]:
    return [
        (start, "\n".join(lines[start : start + size]))
        for start in range(0, max(1, len(lines)), step)
    ]


def count(word: str, chunk: Chunk) -> int:
    if CHINESE.match(word):
        return chunk.folded.count(word)
    return chunk.stems[stem(word)]


Scored = tuple[tuple[float, int], Chunk, list[str]]


class LexicalIndex:
    def __init__(self, chunks: Iterable[Chunk]):
        self.chunks = tuple(chunks)
        self.min_score = math.log(1 + len(self.chunks))
        self.weight = lru_cache(maxsize=8192)(self._weight)

    def _weight(self, word: str) -> float:
        holding = sum(1 for chunk in self.chunks if count(word, chunk))
        return math.log(1 + len(self.chunks) / holding) if holding else 0.0

    def score(
        self, query_words: list[str], keep: Callable[[Chunk], bool] | None = None
    ) -> list[Scored]:
        scored: list[Scored] = []
        for chunk in self.chunks:
            if keep and not keep(chunk):
                continue
            counts = [count(word, chunk) for word in query_words]
            matched = [w for w, c in zip(query_words, counts, strict=True) if c]
            value = round(sum(self.weight(word) for word in matched), 6)
            if matched and (
                len(matched) == len(query_words) or value >= self.min_score
            ):
                scored.append(((value, sum(counts)), chunk, matched))
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored


def without_overlap(ranked: Iterable[Chunk], limit: int, span: int) -> list[Chunk]:
    chosen: list[Chunk] = []
    taken: dict[str, list[int]] = {}
    for chunk in ranked:
        if len(chosen) >= max(1, limit):
            break
        starts = taken.setdefault(chunk.document.id, [])
        if any(abs(chunk.start - other) < span for other in starts):
            continue
        starts.append(chunk.start)
        chosen.append(chunk)
    return chosen
