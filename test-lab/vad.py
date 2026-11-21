"""Regiões de fala/silêncio independentes dos alinhadores (energia RMS)."""

from __future__ import annotations

import math
import struct


def rms_frames(pcm: bytes, sample_rate: int, frame_ms: int = 20) -> list[float]:
    hop = max(1, int(sample_rate * frame_ms / 1000))
    n = len(pcm) // 2
    samples = struct.unpack("<" + "h" * n, pcm)
    frames = []
    for start in range(0, n, hop):
        chunk = samples[start : start + hop]
        if not chunk:
            continue
        acc = sum(value * value for value in chunk) / len(chunk)
        frames.append(math.sqrt(acc))
    return frames


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    k = (len(ordered) - 1) * p / 100.0
    lo = int(k)
    hi = min(lo + 1, len(ordered) - 1)
    if lo == hi:
        return float(ordered[lo])
    return float(ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo))


def speech_intervals_ms(
    pcm: bytes,
    sample_rate: int,
    frame_ms: int = 20,
    min_speech_ms: int = 80,
) -> list[tuple[int, int]]:
    energies = rms_frames(pcm, sample_rate, frame_ms)
    if not energies:
        return []
    peak = max(energies)
    floor = percentile(energies, 20)
    if peak < 80:
        return []
    thresh = floor + 0.22 * (peak - floor)
    thresh = max(thresh, peak * 0.12)
    mask = [energy >= thresh for energy in energies]
    hang = 2
    closed = mask[:]
    for i, spoken in enumerate(mask):
        if spoken:
            continue
        left = any(mask[max(0, i - hang) : i])
        right = any(mask[i + 1 : i + 1 + hang])
        if left and right:
            closed[i] = True
    intervals = []
    start = None
    for i, spoken in enumerate(closed + [False]):
        if spoken and start is None:
            start = i
        elif not spoken and start is not None:
            begin = start * frame_ms
            end = i * frame_ms
            if end - begin >= min_speech_ms:
                intervals.append((begin, end))
            start = None
    return intervals


def speech_coverage_ms(intervals: list[tuple[int, int]]) -> int:
    return sum(end - start for start, end in intervals)


def overlap_ms(start: int, end: int, intervals: list[tuple[int, int]]) -> int:
    total = 0
    for low, high in intervals:
        a = max(start, low)
        b = min(end, high)
        if b > a:
            total += b - a
    return total
