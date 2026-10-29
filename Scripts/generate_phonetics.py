#!/usr/bin/env python3
"""Generate checked-in US/UK IPA drafts for the fixed vocabulary corpus.

The application never invokes eSpeak at runtime. This helper is only used when
the reviewed vocabulary changes; high-risk mathematical names and notation are
kept as explicit human-reviewed overrides below.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import unicodedata
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parents[1]
TERMS_PATH = PROJECT_DIR / "Resources" / "terms.json"
OUTPUT_PATH = PROJECT_DIR / "Scripts" / "term_phonetics.json"

VOICES = {
    "americanIPA": "en-us",
    "britishIPA": "en-gb",
}

VOWELS = set("aeiouyɑɒæɛɜɞɐəɘɵɪʊʌɔɶœøɚɝɨʉɯɤ")
VALID_ONSETS = {
    "p", "b", "t", "d", "k", "ɡ", "g", "f", "v", "θ", "ð", "s", "z",
    "ʃ", "ʒ", "h", "m", "n", "ŋ", "l", "r", "w", "j", "tʃ", "dʒ",
    "pl", "pr", "bl", "br", "tr", "dr", "kl", "kr", "ɡl", "ɡr", "gl",
    "gr", "fl", "fr", "θr", "ʃr", "sp", "st", "sk", "sl", "sm", "sn",
    "sw", "spl", "spr", "str", "skr", "skw", "tw", "dw", "kw", "ɡw",
    "gw", "pj", "bj", "tj", "dj", "kj", "ɡj", "gj", "fj", "vj", "θj",
    "sj", "zj", "hj", "mj", "nj", "lj",
}

# Broad, learner-facing IPA for entries where a generic text-to-phoneme engine
# is especially likely to mishandle a mathematical name, abbreviation, or the
# placement of word stress.
OVERRIDES = {
    "Mathematical Analysis": {
        "americanIPA": "ˌmæθəˈmætɪkəl əˈnæləsɪs",
        "britishIPA": "ˌmæθəˈmætɪkəl əˈnæləsɪs",
    },
    "Advanced Algebra": {
        "americanIPA": "ədˈvænst ˈældʒəbrə",
        "britishIPA": "ədˈvɑːnst ˈældʒɪbrə",
    },
    "Analytic Geometry": {
        "americanIPA": "ˌænəˈlɪtɪk dʒiˈɑːmətri",
        "britishIPA": "ˌænəˈlɪtɪk dʒiˈɒmətri",
    },
    "Probability Theory": {
        "americanIPA": "ˌprɑːbəˈbɪləti ˈθɪri",
        "britishIPA": "ˌprɒbəˈbɪləti ˈθɪəri",
    },
    "Mathematical Statistics": {
        "americanIPA": "ˌmæθəˈmætɪkəl stəˈtɪstɪks",
        "britishIPA": "ˌmæθəˈmætɪkəl stəˈtɪstɪks",
    },
    "Ordinary Differential Equations": {
        "americanIPA": "ˈɔːrdəneri ˌdɪfəˈrɛnʃəl ɪˈkweɪʒənz",
        "britishIPA": "ˈɔːdɪnəri ˌdɪfəˈrɛnʃəl ɪˈkweɪʒənz",
    },
    "Partial Differential Equations": {
        "americanIPA": "ˈpɑːrʃəl ˌdɪfəˈrɛnʃəl ɪˈkweɪʒənz",
        "britishIPA": "ˈpɑːʃəl ˌdɪfəˈrɛnʃəl ɪˈkweɪʒənz",
    },
    "Real Analysis": {
        "americanIPA": "ˈriːəl əˈnæləsɪs",
        "britishIPA": "ˈrɪəl əˈnæləsɪs",
    },
    "Complex Analysis": {
        "americanIPA": "ˈkɑːmplɛks əˈnæləsɪs",
        "britishIPA": "ˈkɒmplɛks əˈnæləsɪs",
    },
    "Functional Analysis": {
        "americanIPA": "ˈfʌŋkʃənəl əˈnæləsɪs",
        "britishIPA": "ˈfʌŋkʃənəl əˈnæləsɪs",
    },
    "Abstract Algebra": {
        "americanIPA": "ˈæbstrækt ˈældʒəbrə",
        "britishIPA": "ˈæbstrækt ˈældʒɪbrə",
    },
    "Bolzano’s theorem": {
        "americanIPA": "bɔːlˈtsɑːnoʊz ˈθɪrəm",
        "britishIPA": "bɒlˈtsɑːnəʊz ˈθɪərəm",
    },
    "Cartesian coordinates": {
        "americanIPA": "kɑːrˈtiːʒən koʊˈɔːrdənəts",
        "britishIPA": "kɑːˈtiːziən kəʊˈɔːdɪnəts",
    },
    "unbounded function": {
        "americanIPA": "ʌnˈbaʊndɪd ˈfʌŋkʃən",
        "britishIPA": "ʌnˈbaʊndɪd ˈfʌŋkʃən",
    },
    "harmonic series, p-series": {
        "americanIPA": "hɑːrˈmɑːnɪk ˈsɪriːz, ˈpiː ˌsɪriːz",
        "britishIPA": "hɑːˈmɒnɪk ˈsɪəriːz, ˈpiː ˌsɪəriːz",
    },
    "limit superior (lim sup)": {
        "americanIPA": "ˈlɪmət suːˈpɪriər, ˌlɪm ˈsʌp",
        "britishIPA": "ˈlɪmɪt suːˈpɪəriə, ˌlɪm ˈsʌp",
    },
    "limit inferior (lim inf)": {
        "americanIPA": "ˈlɪmət ɪnˈfɪriər, ˌlɪm ˈɪnf",
        "britishIPA": "ˈlɪmɪt ɪnˈfɪəriə, ˌlɪm ˈɪnf",
    },
    "Green’s theorem, Green’s formula": {
        "americanIPA": "ˈɡriːnz ˈθɪrəm, ˈɡriːnz ˈfɔːrmjələ",
        "britishIPA": "ˈɡriːnz ˈθɪərəm, ˈɡriːnz ˈfɔːmjʊlə",
    },
    "Eisenstein's criterion": {
        "americanIPA": "ˈaɪzənstaɪnz kraɪˈtɪriən",
        "britishIPA": "ˈaɪzənstaɪnz kraɪˈtɪəriən",
    },
    "Sylvester's law of inertia": {
        "americanIPA": "sɪlˈvɛstərz ˈlɔː əv ɪˈnɜːrʃə",
        "britishIPA": "sɪlˈvɛstəz ˈlɔː əv ɪˈnɜːʃə",
    },
    "n-dimensional": {
        "americanIPA": "ˌɛn daɪˈmɛnʃənəl",
        "britishIPA": "ˌɛn daɪˈmɛnʃənəl",
    },
    "Jordan normal form, Jordan canonical form": {
        "americanIPA": "ˈdʒɔːrdən ˈnɔːrməl fɔːrm, ˈdʒɔːrdən kəˈnɑːnɪkəl fɔːrm",
        "britishIPA": "ˈdʒɔːdən ˈnɔːməl fɔːm, ˈdʒɔːdən kəˈnɒnɪkəl fɔːm",
    },
    "Jordan block": {
        "americanIPA": "ˈdʒɔːrdən blɑːk",
        "britishIPA": "ˈdʒɔːdən blɒk",
    },
    "Poisson Distribution": {
        "americanIPA": "ˈpwɑːsɑːn ˌdɪstrəˈbjuːʃən",
        "britishIPA": "ˈpwɑːsɒn ˌdɪstrɪˈbjuːʃən",
    },
    "Null Hypothesis (H 0)": {
        "americanIPA": "nʌl haɪˈpɑːθəsɪs, ˌeɪtʃ ˈzɪroʊ",
        "britishIPA": "nʌl haɪˈpɒθəsɪs, ˌeɪtʃ ˈzɪərəʊ",
    },
    "Alternative Hypothesis (H 1 or H a)": {
        "americanIPA": "ɔːlˈtɜːrnətɪv haɪˈpɑːθəsɪs, ˌeɪtʃ ˈwʌn ɔːr ˌeɪtʃ ˈeɪ",
        "britishIPA": "ɔːlˈtɜːnətɪv haɪˈpɒθəsɪs, ˌeɪtʃ ˈwʌn ɔː ˌeɪtʃ ˈeɪ",
    },
    "p-value": {
        "americanIPA": "ˈpiː ˌvæljuː",
        "britishIPA": "ˈpiː ˌvæljuː",
    },
}


def move_stress_to_syllable_onset(token: str) -> str:
    """Convert eSpeak's pre-nucleus stress to common dictionary placement."""

    characters = list(token)
    index = 0
    while index < len(characters):
        if characters[index] not in {"ˈ", "ˌ"}:
            index += 1
            continue

        stress = characters.pop(index)
        cluster_end = index
        cluster_start = cluster_end
        while cluster_start > 0:
            candidate = characters[cluster_start - 1]
            if candidate in VOWELS or candidate in {"ˈ", "ˌ", ",", ";", "·"}:
                break
            if candidate in {"ː", "ˑ", "̩", "̯"}:
                break
            cluster_start -= 1

        if cluster_start == 0:
            insertion_index = 0
        else:
            consonant_cluster = "".join(characters[cluster_start:cluster_end])
            insertion_index = cluster_end
            for onset_length in range(len(consonant_cluster), 0, -1):
                onset = consonant_cluster[-onset_length:]
                if onset in VALID_ONSETS:
                    insertion_index = cluster_end - onset_length
                    break

        characters.insert(insertion_index, stress)
        index = max(index + 1, insertion_index + 1)
    return "".join(characters)


def normalize_ipa(raw_ipa: str) -> str:
    """Remove eSpeak joining marks and normalize to readable broad IPA."""

    ipa = unicodedata.normalize("NFC", raw_ipa)
    ipa = ipa.replace("_", "").replace("\u200d", "").replace("\u0361", "")
    ipa = ipa.replace("ᵻ", "ɪ").replace("ɐ", "ə")
    ipa = ipa.replace("ɹ", "r").replace("ɾ", "t")
    ipa = re.sub(r"\s*\n+\s*", ", ", ipa)
    ipa = re.sub(r"\s+", " ", ipa).strip(" /,")
    ipa = " ".join(move_stress_to_syllable_onset(token) for token in ipa.split(" "))
    return ipa


def generate_ipa(espeak_path: str, voice: str, speech_text: str) -> str:
    result = subprocess.run(
        [
            espeak_path,
            "-q",
            "--ipa=1",
            "-v",
            voice,
            speech_text.replace("-", " "),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    ipa = normalize_ipa(result.stdout)
    if not ipa:
        raise ValueError(f"Empty IPA generated for {speech_text!r} ({voice})")
    return ipa


def main() -> None:
    espeak_path = shutil.which("espeak-ng")
    if espeak_path is None:
        raise RuntimeError("espeak-ng is required to refresh term_phonetics.json")

    terms = json.loads(TERMS_PATH.read_text(encoding="utf-8"))
    if len(terms) != 326:
        raise AssertionError(f"Expected 326 terms, got {len(terms)}")

    phonetics: dict[str, dict[str, str]] = {}
    for term in terms:
        speech_text = term["speechText"]
        generated = {
            key: generate_ipa(espeak_path, voice, speech_text)
            for key, voice in VOICES.items()
        }
        generated.update(OVERRIDES.get(speech_text, {}))
        generated["speechText"] = speech_text
        phonetics[str(term["id"])] = generated

    OUTPUT_PATH.write_text(
        json.dumps(phonetics, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(phonetics)} US/UK IPA records to {OUTPUT_PATH}")
    print(f"Human-reviewed overrides: {len(OVERRIDES)}")


if __name__ == "__main__":
    main()
