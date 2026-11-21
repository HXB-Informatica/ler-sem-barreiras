"""Motor actual: whisper-cli Windows + DefaultAlignmentEngine (:lab-cli)."""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def locate_whisper() -> tuple[Path, Path] | None:
    dirs = []
    env = os.environ.get("LER_SEM_BARREIRAS_WHISPER_DIR")
    if env:
        dirs.append(Path(env))
    dirs.append(Path.home() / ".lersembarreiras" / "whisper")
    dirs.append(ROOT / "studio" / "packaging" / "windows")
    for folder in dirs:
        exe = folder / "whisper-cli.exe"
        model = folder / "ggml-base.bin"
        if exe.is_file() and model.is_file() and model.stat().st_size > 1_000_000:
            return exe, model
    return None


def parse_whisper_json(raw: str) -> list[dict]:
    if not raw.strip():
        return []
    root = json.loads(raw)
    words: list[dict] = []
    transcription = root.get("transcription")
    if isinstance(transcription, list) and transcription:
        for item in transcription:
            if not isinstance(item, dict):
                continue
            offsets = item.get("offsets") or {}
            start = int(offsets.get("from") or 0)
            end = int(offsets.get("to") or start)
            tokens = item.get("tokens")
            text = str(item.get("text") or "").strip()
            if isinstance(tokens, list) and tokens:
                dtw = _dtw_words(tokens)
                words.extend(dtw if dtw else _tokens(tokens))
            else:
                words.extend(_split_evenly(text, start, end))
        return [word for word in words if word["text"]]
    segments = (root.get("result") or {}).get("segments") if isinstance(root.get("result"), dict) else root.get("segments")
    if isinstance(segments, list):
        for item in segments:
            if not isinstance(item, dict):
                continue
            start = _time_ms(item.get("start") if item.get("start") is not None else item.get("t0"))
            end = _time_ms(item.get("end") if item.get("end") is not None else item.get("t1"), start)
            tokens = item.get("tokens") or item.get("words")
            if isinstance(tokens, list) and tokens:
                dtw = _dtw_words(tokens)
                words.extend(dtw if dtw else _tokens(tokens))
            else:
                words.extend(_split_evenly(str(item.get("text") or ""), start, end))
    return [word for word in words if word["text"]]


def _dtw_words(tokens: list) -> list[dict]:
    pieces = []
    for item in tokens:
        if not isinstance(item, dict):
            continue
        text = str(item.get("text") or "")
        if not text.strip() or text.lstrip().startswith("["):
            continue
        end = item.get("t_dtw")
        if not isinstance(end, (int, float)):
            continue
        pieces.append((text, int(end), float(item.get("p") or 0.7)))
    if not any(end > 0 for _, end, _ in pieces):
        return []
    groups: list[dict] = []
    for text, end, prob in pieces:
        if not groups or text[:1] in (" ", "\t"):
            groups.append({"text": "", "end": -1, "prob": 0.0, "n": 0})
        group = groups[-1]
        group["text"] += text
        if end > group["end"]:
            group["end"] = end
        group["prob"] += prob
        group["n"] += 1
    cursor = 0
    raw = []
    for group in groups:
        end = cursor if group["end"] < 0 else group["end"]
        start = cursor
        if end < start:
            end = start
        if end > cursor:
            cursor = end
        count = group["n"] or 1
        raw.append((group["text"].strip(), start, end, group["prob"] / count))
    durations = sorted(end - start for _, start, end, _ in raw if end > start)
    max_duration = max(min(durations[len(durations) // 2], 70) * 2, 20) if durations else 10**9
    words = []
    for text, start, end, prob in raw:
        if not text or end <= start:
            continue
        if end - start > max_duration:
            start = end - max_duration
        words.append({"text": text, "startMs": start * 10, "endMs": end * 10, "probability": prob})
    return words


def _tokens(tokens: list) -> list[dict]:
    out = []
    for item in tokens:
        if not isinstance(item, dict):
            continue
        text = str(item.get("text") or item.get("word") or item.get("token") or "").strip()
        text = text.strip(",.!?;:")
        if not text or text.startswith("["):
            continue
        offsets = item.get("offsets") or {}
        start = _time_ms(item.get("t0") if item.get("t0") is not None else item.get("start") or offsets.get("from"))
        end = _time_ms(item.get("t1") if item.get("t1") is not None else item.get("end") or offsets.get("to"), start)
        out.append({"text": text, "startMs": start, "endMs": max(end, start + 20), "probability": 0.7})
    return out


def _split_evenly(text: str, start: int, end: int) -> list[dict]:
    parts = [part.strip(",.!?") for part in text.split() if part.strip(",.!?")]
    if not parts:
        return []
    span = max(end - start, len(parts) * 40)
    step = span // len(parts)
    out = []
    for index, word in enumerate(parts):
        s = start + index * step
        out.append({"text": word, "startMs": s, "endMs": s + max(step, 40), "probability": 0.6})
    return out


def _time_ms(value, fallback: int = 0) -> int:
    if value is None:
        return fallback
    if isinstance(value, bool):
        return fallback
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(round(value * 1000)) if value < 1000 else int(round(value))
    text = str(value).strip().replace(",", ".")
    try:
        number = float(text)
        return int(round(number * 1000)) if number < 1000 else int(round(number))
    except ValueError:
        return fallback


def transcribe_wav(wav: Path, _text: str, work: Path) -> tuple[list[dict], str | None, float]:
    located = locate_whisper()
    if located is None:
        return [], "whisper-cli.exe ou ggml-base.bin não encontrados", 0.0
    exe, model = located
    work.mkdir(parents=True, exist_ok=True)
    prefix = work / "out"
    command = [
        str(exe),
        "-m",
        str(model),
        "-f",
        str(wav),
        "-l",
        "pt",
        "-t",
        "1",
        "-dtw",
        "base",
        "-ojf",
        "-of",
        str(prefix),
    ]
    started = time.perf_counter()
    try:
        completed = subprocess.run(
            command,
            cwd=str(exe.parent),
            capture_output=True,
            timeout=90,
        )
    except subprocess.TimeoutExpired:
        return [], "whisper-cli excedeu 90 s", time.perf_counter() - started
    elapsed = time.perf_counter() - started
    json_path = work / "out.json"
    if not json_path.is_file():
        matches = list(work.glob("*.json"))
        json_path = matches[0] if matches else json_path
    if completed.returncode != 0 and not json_path.is_file():
        err = ((completed.stdout or b"") + (completed.stderr or b"")).decode("utf-8", "replace")[:400]
        return [], f"whisper-cli código {completed.returncode}: {err}", elapsed
    raw = json_path.read_text(encoding="utf-8", errors="replace") if json_path.is_file() else ""
    return parse_whisper_json(raw), None, elapsed
