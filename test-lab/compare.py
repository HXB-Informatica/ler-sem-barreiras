"""Comparação de presença, ordem e timestamps entre dois motores + VAD."""

from __future__ import annotations

from cv_import import tokenize_words
from vad import overlap_ms, speech_coverage_ms, speech_intervals_ms


def _norm(word: str) -> str:
    return "".join(ch for ch in word.lower() if ch.isalnum())


def invalid_intervals(words: list[dict], duration_ms: int) -> list[dict]:
    issues = []
    previous_end = None
    for word in words:
        start, end = word.get("startMs"), word.get("endMs")
        if start is None or end is None:
            continue
        if end < start:
            issues.append({"index": word.get("index"), "type": "INVALID_ORDER", "message": "fim antes do início"})
        if end == start:
            issues.append({"index": word.get("index"), "type": "ZERO_LENGTH", "message": "duração zero"})
        if duration_ms and (start > duration_ms or end > duration_ms + 25):
            issues.append({"index": word.get("index"), "type": "OUTSIDE_AUDIO", "message": "fora do áudio"})
        if previous_end is not None and start < previous_end:
            issues.append({"index": word.get("index"), "type": "OVERLAP", "message": "sobreposição"})
        previous_end = max(previous_end or 0, end)
    return issues


def compare_case(
    text: str,
    duration_ms: int,
    pcm: bytes,
    sample_rate: int,
    engine_a: list[dict],
    engine_b: list[dict],
) -> dict:
    tokens = tokenize_words(text)
    by_a = {word.get("index"): word for word in engine_a}
    by_b = {word.get("index"): word for word in engine_b}
    missing_a = []
    missing_b = []
    start_err = []
    end_err = []
    repeats = []
    previous_norm = None
    for index, token in enumerate(tokens):
        a = by_a.get(index) or {}
        b = by_b.get(index) or {}
        a_ok = a.get("startMs") is not None and a.get("endMs") is not None
        b_ok = b.get("startMs") is not None and b.get("endMs") is not None
        if not a_ok:
            missing_a.append(index)
        if not b_ok:
            missing_b.append(index)
        if a_ok and b_ok:
            start_err.append(abs(int(a["startMs"]) - int(b["startMs"])))
            end_err.append(abs(int(a["endMs"]) - int(b["endMs"])))
        n = _norm(token)
        if previous_norm is not None and n == previous_norm:
            repeats.append(index)
        previous_norm = n or previous_norm
    order_ok = [word.get("index") for word in engine_a] == list(range(len(engine_a)))
    intervals = speech_intervals_ms(pcm, sample_rate)
    speech_ms = speech_coverage_ms(intervals)
    silence_hits = 0
    for word in engine_a:
        start, end = word.get("startMs"), word.get("endMs")
        if start is None or end is None or end <= start:
            continue
        spoken = overlap_ms(int(start), int(end), intervals)
        if spoken / max(1, int(end) - int(start)) < 0.25:
            silence_hits += 1
    expected_ms = max(1, sum(max(2, sum(ch.isalnum() for ch in token)) * 70 for token in tokens))
    ratio = speech_ms / expected_ms if expected_ms else 0
    mismatch = speech_ms < 80 or ratio < 0.25 or ratio > 4.0
    median_div = sorted(start_err)[len(start_err) // 2] if start_err else None
    mean_div = sum(start_err) / len(start_err) if start_err else None
    return {
        "word_count": len(tokens),
        "mapped_a": len(tokens) - len(missing_a),
        "mapped_b": len(tokens) - len(missing_b),
        "missing_a": missing_a,
        "missing_b": missing_b,
        "repeats_in_text": repeats,
        "order_ok_a": order_ok,
        "invalid_a": invalid_intervals(engine_a, duration_ms),
        "invalid_b": invalid_intervals(engine_b, duration_ms),
        "median_divergence_ms": median_div,
        "mean_divergence_ms": mean_div,
        "p95_divergence_ms": sorted(start_err)[int(0.95 * (len(start_err) - 1))] if start_err else None,
        "start_errors_ms": start_err,
        "end_errors_ms": end_err,
        "speech_ms": speech_ms,
        "silence_word_hits_a": silence_hits,
        "possible_mismatch": mismatch,
        "speech_expected_ratio": ratio,
    }
