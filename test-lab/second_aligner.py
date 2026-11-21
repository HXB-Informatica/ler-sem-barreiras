"""Segundo alinhador independente do DefaultAlignmentEngine.

Preferência: Montreal Forced Aligner (MFA) com modelo de português, se `mfa`
estiver no PATH. Alternativa justificada: alinhamento forçado por energia RMS
+ DTW de durações esperadas (sem reconhecimento de fala).
Não é alinhamento fonético de MFA; serve para validação cruzada, não como
referência linguística absoluta.
"""

from __future__ import annotations

import shutil
import subprocess
import tempfile
import time
from pathlib import Path

from cv_import import tokenize_words
from vad import rms_frames, speech_intervals_ms


def mfa_available() -> bool:
    return shutil.which("mfa") is not None


def parse_textgrid_words(text: str) -> list[dict]:
    words = []
    xmin = xmax = None
    label = None
    in_words = False
    for raw in text.splitlines():
        line = raw.strip()
        lowered = line.lower()
        if "name" in lowered and "word" in lowered:
            in_words = True
        if not in_words:
            continue
        if line.startswith("xmin"):
            xmin = float(line.split("=")[-1].strip())
        elif line.startswith("xmax"):
            xmax = float(line.split("=")[-1].strip())
        elif line.startswith("text"):
            label = line.split("=", 1)[-1].strip().strip('"')
            if xmin is not None and xmax is not None and label and label not in {"<unk>", "sil", "spn"}:
                words.append(
                    {
                        "text": label,
                        "startMs": int(round(xmin * 1000)),
                        "endMs": int(round(xmax * 1000)),
                    }
                )
            xmin = xmax = label = None
    return words


def _run_mfa(wav: Path, text: str) -> tuple[list[dict], str | None, float]:
    started = time.perf_counter()
    try:
        with tempfile.TemporaryDirectory(prefix="mfa-lsb-") as raw:
            corpus = Path(raw) / "corpus"
            corpus.mkdir()
            dest = corpus / wav.name
            dest.write_bytes(wav.read_bytes())
            dest.with_suffix(".txt").write_text(text, encoding="utf-8")
            out = Path(raw) / "out"
            completed = subprocess.run(
                ["mfa", "align", str(corpus), "portuguese", "portuguese", str(out), "--clean"],
                capture_output=True,
                text=True,
                timeout=180,
            )
            elapsed = time.perf_counter() - started
            grids = list(out.rglob("*.TextGrid"))
            if completed.returncode != 0 or not grids:
                err = (completed.stderr or completed.stdout or "MFA sem TextGrid")[:400]
                return [], err, elapsed
            parsed = parse_textgrid_words(grids[0].read_text(encoding="utf-8", errors="replace"))
            tokens = tokenize_words(text)
            mapped = []
            for index, token in enumerate(tokens):
                if index < len(parsed):
                    item = parsed[index]
                    mapped.append(
                        {
                            "text": token,
                            "index": index,
                            "startMs": item["startMs"],
                            "endMs": max(item["endMs"], item["startMs"] + 20),
                            "state": "AUTO",
                        }
                    )
                else:
                    mapped.append({"text": token, "index": index, "startMs": None, "endMs": None, "state": "REVIEW"})
            return mapped, None, elapsed
    except Exception as error:  # noqa: BLE001 — falha isolada do segundo motor
        return [], str(error), time.perf_counter() - started


def energy_dtw_align(pcm: bytes, sample_rate: int, text: str) -> list[dict]:
    tokens = tokenize_words(text)
    duration_ms = int(round(1000 * (len(pcm) // 2) / sample_rate)) if sample_rate else 0
    if not tokens:
        return []
    intervals = speech_intervals_ms(pcm, sample_rate)
    if not intervals:
        # sem fala detectada: reparte o ficheiro inteiro (ainda assim produz tempos)
        intervals = [(0, max(duration_ms, len(tokens) * 80))]
    weights = [max(2, sum(1 for ch in token if ch.isalnum())) for token in tokens]
    total_w = sum(weights)
    speech_ms = sum(end - start for start, end in intervals)
    energies = rms_frames(pcm, sample_rate)
    # DTW 1D: mapear pesos cumulativos ao contorno de energia normalizado
    if energies:
        peak = max(energies) or 1.0
        norm = [value / peak for value in energies]
        cumulative = [0.0]
        for value in norm:
            cumulative.append(cumulative[-1] + max(value, 0.02))
        energy_total = cumulative[-1] or 1.0
    else:
        cumulative = [0.0, 1.0]
        energy_total = 1.0
    mapped = []
    acc = 0.0
    frame_ms = 20
    for index, (token, weight) in enumerate(zip(tokens, weights)):
        start_frac = acc / total_w
        acc += weight
        end_frac = acc / total_w
        target_start = start_frac * energy_total
        target_end = end_frac * energy_total
        si = min(range(len(cumulative)), key=lambda i: abs(cumulative[i] - target_start))
        ei = min(range(len(cumulative)), key=lambda i: abs(cumulative[i] - target_end))
        start = max(0, si * frame_ms)
        end = max(start + 20, ei * frame_ms)
        if end > duration_ms > 0:
            end = duration_ms
        mapped.append({"text": token, "index": index, "startMs": start, "endMs": end, "state": "AUTO"})
    # se DTW colapsar, cai na repartição proporcional pelas regiões de fala
    if mapped[-1]["endMs"] <= mapped[0]["startMs"]:
        return _proportional(tokens, weights, intervals)
    _ = speech_ms
    return mapped


def _proportional(tokens: list[str], weights: list[int], intervals: list[tuple[int, int]]) -> list[dict]:
    timeline: list[int] = []
    for start, end in intervals:
        timeline.extend(range(start, end))
    if not timeline:
        timeline = list(range(0, max(200, len(tokens) * 80)))
    total_w = sum(weights) or 1
    mapped = []
    cursor = 0.0
    for index, (token, weight) in enumerate(zip(tokens, weights)):
        span = max(1, int(round(len(timeline) * weight / total_w)))
        a = min(int(cursor), len(timeline) - 1)
        b = min(a + span, len(timeline) - 1)
        mapped.append(
            {
                "text": token,
                "index": index,
                "startMs": timeline[a],
                "endMs": max(timeline[b], timeline[a] + 20),
                "state": "AUTO",
            }
        )
        cursor += span
    return mapped


def align_second(wav: Path, pcm: bytes, sample_rate: int, text: str) -> tuple[list[dict], str, str | None, float]:
    if mfa_available():
        words, error, elapsed = _run_mfa(wav, text)
        if error is None:
            return words, "mfa", None, elapsed
        fallback, _, dt = _timed_energy(pcm, sample_rate, text)
        return fallback, "energy_dtw", f"MFA falhou ({error}); usado energy_dtw", elapsed + dt
    words, elapsed = _timed_energy(pcm, sample_rate, text)
    return words, "energy_dtw", None, elapsed


def _timed_energy(pcm: bytes, sample_rate: int, text: str) -> tuple[list[dict], float]:
    started = time.perf_counter()
    words = energy_dtw_align(pcm, sample_rate, text)
    return words, time.perf_counter() - started
