#!/usr/bin/env python3
"""Laboratório de vozes reais e comparação de alinhadores.

    python test-lab/run_real.py --seed 8 --count 200 --twice
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAB = Path(__file__).resolve().parent
if str(LAB) not in sys.path:
    sys.path.insert(0, str(LAB))

from classify import DISCLAIMER, LABELS, classify_case  # noqa: E402
from compare import compare_case  # noqa: E402
from cv_import import (  # noqa: E402
    CV_LICENSE,
    fetch_parquet,
    load_common_voice_dir,
    load_parquet,
    materialize_row,
    select_sample,
)
from normalize_audio import normalize_to_wav16k, read_pcm16, wav_duration_ms  # noqa: E402
from regression import diff_runs, fingerprint, load_previous, save_history  # noqa: E402
from memory import memory_note  # noqa: E402
from report_real import write_reports  # noqa: E402
from run import gradle_install, run_aligner  # noqa: E402
from second_aligner import align_second, mfa_available  # noqa: E402
from whisper_engine import transcribe_wav  # noqa: E402


def _label_counts(rows: list[dict]) -> dict:
    counts = {name: 0 for name in LABELS}
    for row in rows:
        counts[row["classification"]["primary"]] = counts.get(row["classification"]["primary"], 0) + 1
    return counts


def load_corpus(args) -> tuple[list[dict], dict]:
    if args.cv_root:
        rows = load_common_voice_dir(args.cv_root)
        source = str(args.cv_root)
    else:
        parquet = args.work / "common-voice" / "train.parquet"
        try:
            fetch_parquet(parquet)
            rows = load_parquet(parquet)
            source = "huggingface:remynd/common_voice_pt_pt (derivado de Common Voice; não publicado neste Git)"
        except Exception as error:  # noqa: BLE001
            raise SystemExit(
                "Não foi possível obter Common Voice. Indique --cv-root com validated.tsv e clips/, "
                f"ou permita a descarrega do parquet. Detalhe: {error}"
            ) from error
    chosen, stats = select_sample(rows, args.seed, args.count)
    stats["source"] = source
    return chosen, stats


def prepare_clip(item: dict, index: int, work: Path) -> dict:
    case_id = f"cv{item['locutor_anon'][:6]}-{index:04d}-{item['split']}"
    wav = work / "wav" / f"{case_id}.wav"
    prepared = {
        "id": case_id,
        "split": item["split"],
        "locutor_anon": item["locutor_anon"],
        "text": item["sentence"],
        "wav": wav,
        "wav_rel": f"../wav/{wav.name}",
        "duration_ms": 0,
        "pcm": b"",
        "rate": 16_000,
        "recognized": [],
        "request": None,
        "error_a": None,
        "error_b": None,
        "elapsed_a_ms": 0.0,
        "elapsed_b_ms": 0.0,
        "engine_b": [],
        "second_engine": "energy_dtw",
    }
    try:
        src = materialize_row(item["source_row"], work / "raw", index)
        normalize_to_wav16k(src, wav)
        prepared["pcm"], prepared["rate"] = read_pcm16(wav)
        prepared["duration_ms"] = wav_duration_ms(wav)
    except Exception as error:  # noqa: BLE001
        prepared["error_a"] = prepared["error_b"] = f"normalização: {error}"
        return prepared
    try:
        recognized, whisper_err, whisper_s = transcribe_wav(wav, item["sentence"], work / "whisper" / case_id)
        prepared["elapsed_a_ms"] += whisper_s * 1000
        if whisper_err:
            prepared["error_a"] = whisper_err
        else:
            prepared["recognized"] = recognized
            prepared["request"] = {
                "id": case_id,
                "text": item["sentence"],
                "audioDurationMs": prepared["duration_ms"],
                "recognized": recognized,
            }
    except Exception as error:  # noqa: BLE001
        prepared["error_a"] = f"motor actual: {error}"
    try:
        engine_b, second_name, warn, dt = align_second(wav, prepared["pcm"], prepared["rate"], item["sentence"])
        prepared["engine_b"] = engine_b
        prepared["second_engine"] = second_name
        prepared["elapsed_b_ms"] = dt * 1000
        if warn:
            prepared["error_b"] = None
    except Exception as error:  # noqa: BLE001
        prepared["error_b"] = f"segundo motor: {error}"
    return prepared


def finish_clip(prepared: dict, aligned_words: list[dict], align_share_ms: float) -> dict:
    engine_a = aligned_words
    elapsed_a = prepared["elapsed_a_ms"] + align_share_ms
    comparison = compare_case(
        prepared["text"],
        prepared["duration_ms"],
        prepared["pcm"],
        prepared["rate"],
        engine_a,
        prepared["engine_b"],
    )
    classification = classify_case(comparison, prepared["error_a"], prepared["error_b"])
    return {
        "id": prepared["id"],
        "split": prepared["split"],
        "locutor_anon": prepared["locutor_anon"],
        "text": prepared["text"],
        "wav_rel": prepared["wav_rel"],
        "wav": str(prepared["wav"]),
        "duration_ms": prepared["duration_ms"],
        "engine_a": engine_a,
        "engine_b": prepared["engine_b"],
        "second_engine": prepared["second_engine"],
        "error_a": prepared["error_a"],
        "error_b": prepared["error_b"],
        "elapsed_a_ms": elapsed_a,
        "elapsed_b_ms": prepared["elapsed_b_ms"],
        "comparison": comparison,
        "classification": classification,
    }


def summarize(rows: list[dict], stats: dict, second_engine: str) -> dict:
    words = sum(row["comparison"]["word_count"] for row in rows) or 1
    mapped_a = sum(row["comparison"]["mapped_a"] for row in rows)
    mapped_b = sum(row["comparison"]["mapped_b"] for row in rows)
    critical = [row for row in rows if row["classification"]["needs_investigation"]]
    critical.sort(key=lambda row: (row["classification"]["priority"], -(row["comparison"]["median_divergence_ms"] or 0)))
    elapsed_a = [row["elapsed_a_ms"] for row in rows if row["elapsed_a_ms"]]
    elapsed_b = [row["elapsed_b_ms"] for row in rows if row["elapsed_b_ms"]]
    return {
        "count": len(rows),
        "words": sum(row["comparison"]["word_count"] for row in rows),
        "mapped_pct_a": 100.0 * mapped_a / words,
        "mapped_pct_b": 100.0 * mapped_b / words,
        "missing_words_a": sum(len(row["comparison"]["missing_a"]) for row in rows),
        "invalid_a": sum(len(row["comparison"]["invalid_a"]) for row in rows),
        "overlaps_a": sum(
            1
            for row in rows
            for issue in row["comparison"]["invalid_a"]
            if issue.get("type") == "OVERLAP"
        ),
        "dev_count": sum(1 for row in rows if row["split"] == "dev"),
        "eval_count": sum(1 for row in rows if row["split"] == "eval"),
        "label_counts": _label_counts(rows),
        "critical_count": len(critical),
        "critical": [
            {k: row[k] for k in ("id", "split", "text", "classification", "comparison", "wav_rel") if k in row}
            for row in critical[:40]
        ],
        "mean_elapsed_a_ms": sum(elapsed_a) / len(elapsed_a) if elapsed_a else 0.0,
        "mean_elapsed_b_ms": sum(elapsed_b) / len(elapsed_b) if elapsed_b else 0.0,
        "second_engine": second_engine,
        "license": stats.get("license", CV_LICENSE),
        "import_stats": stats,
        "disclaimer": DISCLAIMER,
        "precision_claim": "não estimada — sem referência temporal independente fiável",
        "memory": memory_note(),
        "fingerprint": fingerprint(rows),
        "fingerprint_second": fingerprint(
            [
                {
                    **row,
                    "comparison": {
                        **row["comparison"],
                        "mapped_a": row["comparison"]["mapped_b"],
                        "median_divergence_ms": 0,
                    },
                    "classification": {"primary": row["second_engine"]},
                }
                for row in rows
            ]
        ),
        "cases": rows,
    }


def run_once(work: Path, args, lab_cli: Path) -> dict:
    work.mkdir(parents=True, exist_ok=True)
    chosen, stats = load_corpus(args)
    if not chosen:
        raise SystemExit("Nenhuma gravação pt-PT elegível. Forneça --cv-root com metadados variant/accents.")
    prepared_rows = []
    for index, item in enumerate(chosen):
        if index % 10 == 0:
            print(f"  {index}/{len(chosen)} {item['split']}", flush=True)
        try:
            prepared_rows.append(prepare_clip(item, index, work))
        except Exception as error:  # noqa: BLE001
            prepared_rows.append(
                {
                    "id": f"cv-fail-{index:04d}",
                    "split": item.get("split", "eval"),
                    "locutor_anon": item.get("locutor_anon", "unknown"),
                    "text": item.get("sentence", ""),
                    "wav": work / "wav" / "missing.wav",
                    "wav_rel": "",
                    "duration_ms": 0,
                    "pcm": b"",
                    "rate": 16000,
                    "recognized": [],
                    "request": None,
                    "error_a": f"falha isolada: {error}",
                    "error_b": traceback.format_exc()[-400:],
                    "elapsed_a_ms": 0.0,
                    "elapsed_b_ms": 0.0,
                    "engine_b": [],
                    "second_engine": "none",
                }
            )
    requests_path = work / "requests.jsonl"
    aligned_path = work / "aligned.jsonl"
    batch = [row for row in prepared_rows if row.get("request")]
    align_share = 0.0
    aligned_by_id: dict[str, list] = {}
    if batch:
        requests_path.write_text(
            "".join(json.dumps(row["request"], ensure_ascii=False) + "\n" for row in batch),
            encoding="utf-8",
        )
        wall = run_aligner(lab_cli, requests_path, aligned_path)
        align_share = (wall * 1000) / len(batch)
        for line in aligned_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            payload = json.loads(line)
            aligned_by_id[payload["id"]] = payload.get("words") or []
    rows = [
        finish_clip(row, aligned_by_id.get(row["id"], []), align_share if row.get("request") else 0.0)
        for row in prepared_rows
    ]
    second = rows[0]["second_engine"] if rows else ("mfa" if mfa_available() else "energy_dtw")
    summary = summarize(rows, stats, second)
    history_dir = args.work / "history"
    previous = load_previous(history_dir)
    saved = save_history(history_dir, summary)
    if previous:
        summary["regression"] = diff_runs(previous, summary)
    else:
        summary["regression"] = {"note": "Primeira execução neste histórico."}
    summary["history_files"] = [path.name for path in sorted(history_dir.glob("20*.json"))]
    summary["history_saved"] = saved.name
    write_reports(summary, work / "reports")
    return summary


def copy_examples(summary: dict, reports: Path) -> None:
    dest = ROOT / "docs" / "test-lab" / "real"
    dest.mkdir(parents=True, exist_ok=True)
    slim = {key: summary[key] for key in summary if key not in {"cases"}}
    slim["critical"] = slim.get("critical", [])[:8]
    (dest / "example-summary.json").write_text(json.dumps(slim, ensure_ascii=False, indent=2), encoding="utf-8")
    html = (reports / "dashboard.html").read_text(encoding="utf-8")
    (dest / "example-dashboard.html").write_text(html, encoding="utf-8")
    (dest / "problemas-alinhador.md").write_text((reports / "problemas-alinhador.md").read_text(encoding="utf-8"), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=8)
    parser.add_argument("--count", type=int, default=200)
    parser.add_argument("--twice", action="store_true")
    parser.add_argument("--cv-root", type=Path, default=None)
    parser.add_argument("--work", type=Path, default=LAB / "work" / "real")
    args = parser.parse_args()
    lab_cli = gradle_install()
    first = args.work / "run-a"
    t0 = time.perf_counter()
    summary_a = run_once(first, args, lab_cli)
    print(
        f"Run A: {summary_a['count']} gravações, mapeadas {summary_a['mapped_pct_a']:.1f}%, "
        f"fingerprint {summary_a['fingerprint']} ({time.perf_counter() - t0:.1f}s)"
    )
    if args.twice:
        summary_b = run_once(args.work / "run-b", args, lab_cli)
        print(f"Run B: {summary_b['count']} gravações, fingerprint {summary_b['fingerprint']}")
        if summary_a["fingerprint_second"] != summary_b["fingerprint_second"]:
            print("FALHA: o segundo motor não reproduziu os timestamps.", file=sys.stderr)
            return 2
        if summary_a["fingerprint"] != summary_b["fingerprint"]:
            print(
                "AVISO: o motor actual (Whisper) não foi bit-a-bit idêntico nas duas corridas; "
                "o segundo motor foi reproduzível.",
                file=sys.stderr,
            )
    copy_examples(summary_a, first / "reports")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
