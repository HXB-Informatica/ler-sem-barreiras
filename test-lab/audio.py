"""PCM sintético por concatenação. Tempos = limites de segmentos, não fonética natural."""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 16_000
REFERENCE_KIND = "synthetic_concat_pcm"
DISCLAIMER = (
    "Os tempos de referência são limites de segmentos PCM concatenados "
    "(palavra + pausa), não fronteiras fonéticas de fala natural. "
    "Validam o algoritmo em condições controladas e não substituem testes com voz humana."
)


def samples_to_ms(n_samples: int) -> int:
    return int(round(n_samples * 1000 / SAMPLE_RATE))


def ms_to_samples(ms: int) -> int:
    return int(round(SAMPLE_RATE * ms / 1000))


def word_duration_ms(word: str, speed: float) -> int:
    letters = max(2, sum(1 for ch in word if ch.isalnum()))
    base = max(90, letters * 70)
    return max(60, int(round(base * speed)))


def silence_pcm(ms: int) -> bytes:
    return b"\x00\x00" * ms_to_samples(ms)


def word_pcm(word: str, duration_ms: int) -> bytes:
    n = ms_to_samples(duration_ms)
    freq = 180.0 + (sum(ord(ch) for ch in word) % 520)
    fade = min(80, n // 8)
    out = bytearray()
    for i in range(n):
        envelope = 1.0
        if i < fade:
            envelope = i / fade
        elif i > n - fade:
            envelope = (n - i) / fade
        sample = int(8500 * envelope * math.sin(2 * math.pi * freq * i / SAMPLE_RATE))
        sample = max(-32767, min(32767, sample))
        out.extend(struct.pack("<h", sample))
    return bytes(out)


def add_noise(pcm: bytes, level: float, rng) -> bytes:
    if level <= 0:
        return pcm
    samples = list(struct.unpack("<" + "h" * (len(pcm) // 2), pcm))
    amp = int(level * 4000)
    noisy = []
    for value in samples:
        value = value + rng.randint(-amp, amp)
        noisy.append(max(-32767, min(32767, value)))
    return struct.pack("<" + "h" * len(noisy), *noisy)


def concatenate_sentence(
    words: list[str], speed: float, pause_ms: int, noise: float, rng
) -> tuple[bytes, list[dict], int]:
    chunks: list[bytes] = []
    timed: list[dict] = []
    cursor = 0
    for index, word in enumerate(words):
        dur = word_duration_ms(word, speed)
        segment = word_pcm(word, dur)
        start = samples_to_ms(cursor)
        cursor += len(segment) // 2
        end = samples_to_ms(cursor)
        timed.append(
            {
                "text": word,
                "index": index,
                "start_ms": start,
                "end_ms": end,
                "source": REFERENCE_KIND,
            }
        )
        chunks.append(segment)
        if index != len(words) - 1:
            gap = silence_pcm(pause_ms)
            chunks.append(gap)
            cursor += len(gap) // 2
    pcm = add_noise(b"".join(chunks), noise, rng)
    duration_ms = samples_to_ms(len(pcm) // 2)
    return pcm, timed, duration_ms


def write_wav(path: Path, pcm: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(pcm)
