from __future__ import annotations

import json
import random
from pathlib import Path

from audio import DISCLAIMER, REFERENCE_KIND, concatenate_sentence, write_wav
from phrases import CANONICAL, CATEGORIES, phrase_for


def tokenize(text: str) -> list[str]:
    return text.split()


def mutate_recognized(words: list[dict], category: str, rng) -> list[dict]:
    recognized = [
        {
            "text": item["text"],
            "startMs": item["start_ms"],
            "endMs": item["end_ms"],
            "probability": 0.95,
        }
        for item in words
    ]
    if not recognized:
        return recognized
    if category == "asr_omit" and len(recognized) > 1:
        del recognized[rng.randrange(len(recognized))]
    elif category == "asr_substitute":
        i = rng.randrange(len(recognized))
        recognized[i] = dict(recognized[i])
        recognized[i]["text"] = "hum" if recognized[i]["text"] != "hum" else "ah"
        recognized[i]["probability"] = 0.4
    elif category == "asr_extra":
        i = rng.randrange(len(recognized))
        extra = {
            "text": "eh",
            "startMs": max(0, recognized[i]["startMs"] - 40),
            "endMs": recognized[i]["startMs"],
            "probability": 0.3,
        }
        recognized.insert(i, extra)
    return recognized


def generate_cases(out_dir: Path, seed: int, count: int) -> Path:
    rng = random.Random(seed)
    cases_dir = out_dir / "cases"
    wav_dir = out_dir / "wav"
    cases_dir.mkdir(parents=True, exist_ok=True)
    requests = out_dir / "requests.jsonl"
    catalog = []
    with requests.open("w", encoding="utf-8") as jsonl:
        for index in range(count):
            category = "canonical" if index == 0 else CATEGORIES[index % len(CATEGORIES)]
            text = CANONICAL if index == 0 else phrase_for(index, category, rng)
            tokens = tokenize(text)
            if not tokens:
                tokens = tokenize(CANONICAL)
                text = CANONICAL
            speed = 0.65 if category == "speed_fast" else 1.55 if category == "speed_slow" else 1.0
            pause_ms = 420 if category == "pause_long" else 40 + (index % 5) * 25
            noise = 0.12 if category == "noise" else 0.0
            case_id = f"s{seed:04d}-{index:04d}-{category}"
            pcm, timed, duration_ms = concatenate_sentence(tokens, speed, pause_ms, noise, rng)
            write_wav(wav_dir / f"{case_id}.wav", pcm)
            recognized = mutate_recognized(timed, category, rng)
            case = {
                "id": case_id,
                "seed": seed,
                "index": index,
                "category": category,
                "text": text,
                "speed": speed,
                "pause_ms": pause_ms,
                "noise": noise,
                "audio_duration_ms": duration_ms,
                "reference_kind": REFERENCE_KIND,
                "disclaimer": DISCLAIMER,
                "words": timed,
                "wav": f"wav/{case_id}.wav",
            }
            (cases_dir / f"{case_id}.json").write_text(
                json.dumps(case, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            catalog.append(case)
            jsonl.write(
                json.dumps(
                    {
                        "id": case_id,
                        "text": text,
                        "audioDurationMs": duration_ms,
                        "recognized": recognized,
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
    (out_dir / "catalog.json").write_text(
        json.dumps({"seed": seed, "count": count, "disclaimer": DISCLAIMER, "cases": catalog}, ensure_ascii=False),
        encoding="utf-8",
    )
    return requests
