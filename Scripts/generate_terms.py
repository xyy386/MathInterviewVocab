#!/usr/bin/env python3
"""Generate the app vocabulary data from the reviewed Markdown source."""

from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATH = PROJECT_ROOT / "docs" / "vocabulary.md"
OUTPUT_PATH = PROJECT_ROOT / "Resources" / "terms.json"
PHONETICS_PATH = PROJECT_ROOT / "Scripts" / "term_phonetics.json"

SECTION_SPECS = {
    "一、常用数学学科名称": {
        "id": "subjects",
        "name": "常用数学学科名称",
        "name_en": "Common Mathematics Subjects",
        "start": 1,
        "end": 11,
        "count": 11,
    },
    "二、数学分析": {
        "id": "analysis",
        "name": "数学分析",
        "name_en": "Mathematical Analysis",
        "start": 12,
        "end": 174,
        "count": 163,
    },
    "三、高等代数": {
        "id": "algebra",
        "name": "高等代数",
        "name_en": "Advanced Algebra",
        "start": 175,
        "end": 284,
        "count": 110,
    },
    "四、概率论": {
        "id": "probability",
        "name": "概率论",
        "name_en": "Probability Theory",
        "start": 285,
        "end": 311,
        "count": 27,
    },
    "五、数理统计": {
        "id": "statistics",
        "name": "数理统计",
        "name_en": "Mathematical Statistics",
        "start": 312,
        "end": 326,
        "count": 15,
    },
}

SECTION_RE = re.compile(r"^##\s+(.+?)\s*$")
ITEM_RE = re.compile(r"^(\d+)\.\s+(.+?)\s*$")
MARKDOWN_LINK_RE = re.compile(r"!?\[([^\]]*)\]\([^)]+\)")
LATEX_WRAPPER_RE = re.compile(
    r"\\(?:text|mathrm|mathbf|mathit|mathsf|operatorname)\s*\{([^{}]*)\}"
)
LATEX_COMMAND_RE = re.compile(r"\\([A-Za-z]+)")
LATEX_SCRIPT_RE = re.compile(r"([A-Za-z0-9])[_^]\{?([A-Za-z0-9]+)\}?")

# Remove parenthetical acronyms such as (GCD), （CDF）, and the plural (ODEs).
# Parenthetical explanations and mathematical symbols such as (lim sup) and
# (H_0) are deliberately retained.
ACRONYM_PARENS_RE = re.compile(r"[（(]\s*[A-Z]{2,}s?\s*[）)]")


def make_speech_text(english: str) -> str:
    """Return an English term suitable for text-to-speech."""

    text = MARKDOWN_LINK_RE.sub(r"\1", english)
    text = ACRONYM_PARENS_RE.sub("", text)

    # Strip LaTeX while retaining its readable content.
    text = text.replace(r"\(", "").replace(r"\)", "")
    while LATEX_WRAPPER_RE.search(text):
        text = LATEX_WRAPPER_RE.sub(r"\1", text)
    text = LATEX_COMMAND_RE.sub(r"\1", text)
    text = LATEX_SCRIPT_RE.sub(r"\1 \2", text)
    text = text.replace("{", "").replace("}", "")
    text = text.replace("$", "")

    # Remove remaining Markdown markers after LaTeX subscripts are expanded.
    text = text.replace("**", "").replace("__", "")
    text = text.translate(str.maketrans({"*": "", "`": "", "~": "", "_": " "}))

    # Turn written alternatives into pauses that TTS voices naturally.
    text = text.replace("或", " or ")
    text = re.sub(r"\s*[;/／]\s*", ", ", text)
    text = text.replace("（", " (").replace("）", ")")

    text = re.sub(r"\s*,\s*", ", ", text)
    text = re.sub(r"\(\s+", "(", text)
    text = re.sub(r"\s+\)", ")", text)
    text = re.sub(r"\s+", " ", text).strip(" ,")

    if not text:
        raise ValueError(f"Empty speech text generated from {english!r}")
    if re.search(r"\\[A-Za-z]+|\\[()]|[*_`$]", text):
        raise ValueError(
            f"Markdown or LaTeX remained in speech text: {english!r} -> {text!r}"
        )
    return text


def parse_term_block(term_block: str, line_number: int) -> tuple[str, str, bool]:
    """Split a bold-aware bilingual term block into Chinese and English."""

    important = term_block.startswith("**")
    if important:
        if not term_block.endswith("**"):
            raise ValueError(f"Unclosed outer bold marker on line {line_number}")
        term_block = term_block[2:-2]
    elif term_block.endswith("**"):
        raise ValueError(f"Unexpected closing bold marker on line {line_number}")

    open_index = term_block.find("（")
    close_index = term_block.rfind("）")
    if open_index <= 0 or close_index != len(term_block) - 1:
        raise ValueError(
            f"Expected 中文（English） term structure on line {line_number}: "
            f"{term_block!r}"
        )

    chinese = term_block[:open_index].strip()
    english = term_block[open_index + 1 : close_index].strip()
    if not chinese or not english:
        raise ValueError(f"Empty bilingual term on line {line_number}")
    return chinese, english, important


def parse_source(source_path: Path) -> tuple[list[dict[str, Any]], list[str]]:
    """Parse all terms and return the records plus encountered section headings."""

    terms: list[dict[str, Any]] = []
    encountered_sections: list[str] = []
    current_section: dict[str, Any] | None = None

    for line_number, raw_line in enumerate(
        source_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        section_match = SECTION_RE.match(raw_line)
        if section_match:
            heading = section_match.group(1)
            if heading not in SECTION_SPECS:
                raise ValueError(f"Unknown section on line {line_number}: {heading!r}")
            if heading in encountered_sections:
                raise ValueError(f"Duplicate section on line {line_number}: {heading!r}")
            encountered_sections.append(heading)
            current_section = SECTION_SPECS[heading]
            continue

        item_match = ITEM_RE.match(raw_line)
        if not item_match:
            continue
        if current_section is None:
            raise ValueError(f"Term appears before a section on line {line_number}")

        term_id = int(item_match.group(1))
        body = item_match.group(2)
        term_block, separator, definition = body.partition("：")
        if not separator or not definition.strip():
            raise ValueError(
                f"Expected a fullwidth colon and definition on line {line_number}"
            )

        chinese, english, important = parse_term_block(
            term_block.strip(), line_number
        )
        terms.append(
            {
                "id": term_id,
                "subjectID": current_section["id"],
                "subjectName": current_section["name"],
                "subjectNameEn": current_section["name_en"],
                "chinese": chinese,
                "english": english,
                "definition": definition.strip(),
                "important": important,
                "speechText": make_speech_text(english),
            }
        )

    return terms, encountered_sections


def validate_terms(
    terms: list[dict[str, Any]], encountered_sections: list[str]
) -> None:
    """Fail generation if the reviewed corpus structure changes unexpectedly."""

    if encountered_sections != list(SECTION_SPECS):
        raise AssertionError(
            "Expected exactly five sections in the reviewed order; got "
            f"{encountered_sections!r}"
        )
    if len(terms) != 326:
        raise AssertionError(f"Expected 326 terms, got {len(terms)}")

    term_ids = [term["id"] for term in terms]
    if term_ids != list(range(1, 327)):
        raise AssertionError("Term IDs must be continuous from 1 through 326")

    subject_counts = Counter(term["subjectID"] for term in terms)
    expected_counts = {
        spec["id"]: spec["count"] for spec in SECTION_SPECS.values()
    }
    if subject_counts != expected_counts:
        raise AssertionError(
            f"Unexpected subject counts: {dict(subject_counts)!r}; "
            f"expected {expected_counts!r}"
        )

    for spec in SECTION_SPECS.values():
        ids = [
            term["id"] for term in terms if term["subjectID"] == spec["id"]
        ]
        if ids != list(range(spec["start"], spec["end"] + 1)):
            raise AssertionError(
                f"Unexpected ID range for subject {spec['id']!r}: {ids!r}"
            )

    important_count = sum(bool(term["important"]) for term in terms)
    if important_count != 130:
        raise AssertionError(f"Expected 130 important terms, got {important_count}")

    for term in terms:
        pronunciation = term.get("pronunciation")
        if not isinstance(pronunciation, dict):
            raise AssertionError(f"Missing pronunciation for term {term['id']}")
        for key in ("americanIPA", "britishIPA"):
            ipa = pronunciation.get(key)
            if not isinstance(ipa, str) or not ipa.strip():
                raise AssertionError(
                    f"Missing {key} for term {term['id']}: {term['english']}"
                )


def merge_phonetics(terms: list[dict[str, Any]]) -> None:
    """Merge the checked-in, speech-text-matched IPA records into each term."""

    if not PHONETICS_PATH.is_file():
        raise FileNotFoundError(f"Phonetics data not found: {PHONETICS_PATH}")

    phonetics = json.loads(PHONETICS_PATH.read_text(encoding="utf-8"))
    expected_ids = {str(term["id"]) for term in terms}
    if set(phonetics) != expected_ids:
        raise AssertionError("IPA records must cover term IDs 1 through 326 exactly")

    for term in terms:
        record = phonetics[str(term["id"])]
        if record.get("speechText") != term["speechText"]:
            raise AssertionError(
                "IPA speech text is stale for term "
                f"{term['id']}: {record.get('speechText')!r} != "
                f"{term['speechText']!r}"
            )
        term["pronunciation"] = {
            "americanIPA": record["americanIPA"],
            "britishIPA": record["britishIPA"],
        }


def main() -> None:
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError(f"Markdown source not found: {SOURCE_PATH}")

    terms, encountered_sections = parse_source(SOURCE_PATH)
    merge_phonetics(terms)
    validate_terms(terms, encountered_sections)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(
        json.dumps(terms, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    counts = Counter(term["subjectID"] for term in terms)
    important_count = sum(bool(term["important"]) for term in terms)
    print(f"Wrote {len(terms)} terms to {OUTPUT_PATH}")
    print(f"Subjects: {dict(counts)}")
    print(f"Important: {important_count}")


if __name__ == "__main__":
    main()
