"""Fetch the programs NCU opens to international degree students.

The admission system lists every program with its teaching language, the
language proof it asks for at admission, its graduation credits and a link to
the department's own graduation rules. This script reads that page in English
and in Chinese and writes `app/data/graduation_language/programs.json`.

    uv run python scripts/build_intl_programs.py
    uv run python scripts/build_intl_programs.py --intake 2027_Spring_Semester_Admission_for_International_Degree_Students
"""

import argparse
import json
import sys
import time
from datetime import date
from pathlib import Path

import bs4
import httpx

BASE = "https://cis.ncu.edu.tw/AdmissionSys"
DEFAULT_INTAKE = "2027_Fall_Semester_Admission_for_International_Degree_Students"
OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "app/data/graduation_language/programs.json"
)
HEADERS = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0.0.0"}

DEGREES = {"Bachelor": "bachelor", "Master": "master", "Doctor": "phd"}
# The page's own three categories (`data-openclass`).
TEACHING_LANGUAGES = {"1": "chinese", "2": "english_sufficient", "3": "english"}
LANGUAGE_ITEMS = {"Language Proficiency", "語言能力證明"}
# Order of the four lines in the graduation column.
GRADUATION_FIELDS = ["years", "credits", "elective_credits", "required_credits"]


def text_of(node: bs4.Tag) -> str:
    return " ".join(node.get_text(" ").split())


def parse_programs(html: str) -> list[dict]:
    soup = bs4.BeautifulSoup(html, "html.parser")
    colleges = {
        option["value"]: text_of(option)
        for option in soup.select("select#college option, select[name=college] option")
        if option.get("value")
    }
    programs = []
    for row in soup.select("tr.department-row"):
        cells = row.find_all("td", recursive=False)
        language_note = ""
        for item in cells[1].select("li.list-group-item"):
            label = item.find("p")
            if label and text_of(label).lstrip("* ") in LANGUAGE_ITEMS:
                label.extract()
                language_note = text_of(item)
        values = [text_of(p).partition("：")[2] for p in cells[2].find_all("p")]
        regulation = cells[2].find("a")
        website = cells[4].find("a") if len(cells) > 4 else None
        programs.append(
            {
                "name": text_of(cells[0]),
                "degree": DEGREES[row["data-systemcode"]],
                "teaching_language": TEACHING_LANGUAGES[row["data-openclass"]],
                "college": colleges.get(row["data-college"], ""),
                "admission_language_note": language_note,
                "graduation": dict(zip(GRADUATION_FIELDS, values, strict=False)),
                "regulation_url": regulation["href"] if regulation else None,
                "website": website["href"] if website else None,
            }
        )
    return programs


def merge(english: list[dict], chinese: list[dict]) -> list[dict]:
    """Pair the two locales, which list the same programs in the same order."""
    if len(english) != len(chinese):
        raise ValueError(f"{len(english)} English rows but {len(chinese)} Chinese")
    merged = []
    for en, zh in zip(english, chinese, strict=True):
        if (en["degree"], en["website"]) != (zh["degree"], zh["website"]):
            raise ValueError(f"rows out of step at {en['name']} / {zh['name']}")
        merged.append(
            {
                "name": {"zh": zh["name"], "en": en["name"]},
                "degree": en["degree"],
                "teaching_language": en["teaching_language"],
                "college": {"zh": zh["college"], "en": en["college"]},
                "admission_language_note": {
                    "zh": zh["admission_language_note"],
                    "en": en["admission_language_note"],
                },
                "graduation": en["graduation"],
                "regulation_url": en["regulation_url"],
                "website": en["website"],
            }
        )
    return merged


def get(
    client: httpx.Client, url: str, follow_redirects: bool = True
) -> httpx.Response:
    """GET with a few retries: NCU hosts drop connections now and then."""
    for attempt in range(4):
        try:
            response = client.get(url, follow_redirects=follow_redirects)
            return response if response.is_redirect else response.raise_for_status()
        except httpx.TransportError:
            if attempt == 3:
                raise
            time.sleep(attempt + 1)
    raise AssertionError("unreachable")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--intake", default=DEFAULT_INTAKE)
    args = parser.parse_args()
    url = f"{BASE}/oia/currentPrograms/{args.intake}"

    with httpx.Client(headers=HEADERS, timeout=60) as client:
        pages = {}
        for locale in ("en", "tw"):
            # Sets the language cookie; its redirect leads nowhere useful.
            client.cookies.clear()
            get(client, f"{BASE}/locale/{locale}", follow_redirects=False)
            pages[locale] = parse_programs(get(client, url).text)

    programs = merge(pages["en"], pages["tw"])
    if not programs:
        print(f"no programs found at {url}", file=sys.stderr)
        return 1
    OUTPUT.write_text(
        json.dumps(
            {
                "intake": args.intake.replace("_", " "),
                "source_url": url,
                "fetched_on": date.today().isoformat(),
                "programs": programs,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"{len(programs)} programs -> {OUTPUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
