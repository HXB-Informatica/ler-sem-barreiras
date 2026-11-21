"""Normalização para WAV PCM 16 kHz mono 16-bit, via ffmpeg quando necessário."""

from __future__ import annotations

import shutil
import subprocess
import wave
from pathlib import Path

TARGET_RATE = 16_000


def find_ffmpeg() -> str | None:
    return shutil.which("ffmpeg")


def wav_duration_ms(path: Path) -> int:
    with wave.open(str(path), "rb") as handle:
        frames = handle.getnframes()
        rate = handle.getframerate()
        if rate <= 0:
            return 0
        return int(round(1000 * frames / rate))


def read_pcm16(path: Path) -> tuple[bytes, int]:
    with wave.open(str(path), "rb") as handle:
        if handle.getnchannels() != 1 or handle.getsampwidth() != 2:
            raise ValueError(f"WAV não está em PCM 16-bit mono: {path}")
        return handle.readframes(handle.getnframes()), handle.getframerate()


def normalize_to_wav16k(src: Path, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg = find_ffmpeg()
    if ffmpeg:
        subprocess.run(
            [
                ffmpeg,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(src),
                "-ac",
                "1",
                "-ar",
                str(TARGET_RATE),
                "-sample_fmt",
                "s16",
                str(dest),
            ],
            check=True,
        )
        return dest
    if src.suffix.lower() == ".wav":
        pcm, rate = read_pcm16(src)
        if rate != TARGET_RATE:
            raise RuntimeError("ffmpeg ausente e a taxa do WAV não é 16 kHz.")
        dest.write_bytes(src.read_bytes())
        return dest
    raise RuntimeError("ffmpeg não está no PATH; não é possível converter MP3/Opus do Common Voice.")
