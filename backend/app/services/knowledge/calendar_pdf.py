import argparse
import json
import re
import sys
from datetime import date
from pathlib import Path

from pypdf import PdfReader

ROC_OFFSET = 1911
HEADER = re.compile(r"^年\s+月\s+週")
MINUTES = re.compile(r"^\d+次行政會議通過")
GRID_PREFIX = re.compile(r"^(?:\d+(?:\s+|$))*")
EVENT_START = re.compile(
    r"(?<!\S)(\d{1,2})日(?:起)?"
    r"(?:～(?:(\d{3})年)?(?:(\d{1,2})月)?(\d{1,2})日)?(?=\s)"
)
SEPARATOR = re.compile(r"\s*；\s*")


def page_events(
    text: str, year: int, month: int, semester: str
) -> list[dict[str, str]]:
    events: list[dict[str, str]] = []
    previous_day = 0
    started = False
    for raw in text.splitlines():
        line = raw.strip()
        if not started:
            started = bool(HEADER.match(line))
            continue
        if MINUTES.match(line):
            break
        rest = line[GRID_PREFIX.match(line).end() :].strip()
        if not rest:
            continue
        marks = list(EVENT_START.finditer(rest))
        lead = rest[: marks[0].start()] if marks else rest
        if lead.strip() and events:
            events[-1]["text"] += lead.strip()
        for index, mark in enumerate(marks):
            stop = marks[index + 1].start() if index + 1 < len(marks) else len(rest)
            day = int(mark.group(1))
            if day < previous_day:
                year, month = (year + 1, 1) if month == 12 else (year, month + 1)
            previous_day = day
            start = date(year, month, day)
            end = start
            if mark.group(4):
                end_month = int(mark.group(3)) if mark.group(3) else month
                end_year = year + (end_month < month)
                if mark.group(2):
                    end_year = int(mark.group(2)) + ROC_OFFSET
                end = date(end_year, end_month, int(mark.group(4)))
                if end < start:
                    raise ValueError(f"range ends before it starts: {mark.group(0)}")
            for part in SEPARATOR.split(rest[mark.end() : stop].strip()):
                events.append(
                    {
                        "start": start.isoformat(),
                        "end": end.isoformat(),
                        "text": part,
                        "semester": semester,
                    }
                )
    return events


def convert(
    pages: list[str], source: str, fetched_on: str, academic_year: int
) -> dict[str, object]:
    west = academic_year + ROC_OFFSET
    starts = ((west, 8), (west + 1, 2))
    events = []
    for number, (text, (year, month)) in enumerate(
        zip(pages, starts, strict=True), start=1
    ):
        events.extend(page_events(text, year, month, f"{academic_year}-{number}"))
    return {
        "source": source,
        "fetched_on": fetched_on,
        "academic_year": academic_year,
        "events": events,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Turn the two-page NCU academic calendar PDF into the JSON "
        "the knowledge base reads."
    )
    parser.add_argument("pdf", type=Path, help="the downloaded calendar PDF")
    parser.add_argument("--source", required=True, help="official URL of the PDF")
    parser.add_argument("--fetched-on", required=True, help="ISO date of the download")
    parser.add_argument("--academic-year", type=int, required=True, help="ROC year")
    parser.add_argument("--output", type=Path, help="write here instead of stdout")
    args = parser.parse_args(argv)
    pages = [page.extract_text() for page in PdfReader(args.pdf).pages]
    result = convert(pages, args.source, args.fetched_on, args.academic_year)
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
