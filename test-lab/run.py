#!/usr/bin/env python3
"""Gera casos sintéticos, corre o alinhador da aplicação e escreve relatórios.

Uso (na raiz do repositório):

    python test-lab/run.py
    python test-lab/run.py --seed 7 --count 500 --twice
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAB = Path(__file__).resolve().parent
if str(LAB) not in sys.path:
    sys.path.insert(0, str(LAB))

from evaluate import evaluate  # noqa: E402
from generate import generate_cases  # noqa: E402
from report import write_reports  # noqa: E402


def fingerprint(summary: dict) -> str:
    payload = json.dumps(summary["fingerprint"], sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def gradle_install() -> Path:
    wrapper = ROOT / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
    subprocess.run(
        [str(wrapper), ":lab-cli:installDist"],
        cwd=ROOT,
        check=True,
    )
    if sys.platform.startswith("win"):
        exe = ROOT / "lab-cli" / "build" / "install" / "lab-cli" / "bin" / "lab-cli.bat"
    else:
        exe = ROOT / "lab-cli" / "build" / "install" / "lab-cli" / "bin" / "lab-cli"
    if not exe.exists():
        raise FileNotFoundError(f"lab-cli não foi instalado em {exe}")
    return exe


def run_aligner(exe: Path, requests: Path, aligned: Path) -> float:
    aligned.parent.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    subprocess.run([str(exe), str(requests), str(aligned)], cwd=ROOT, check=True)
    return time.perf_counter() - started


def run_once(work: Path, seed: int, count: int, exe: Path) -> dict:
    if work.exists():
        for path in work.glob("**/*"):
            if path.is_file():
                path.unlink()
    requests = generate_cases(work, seed, count)
    aligned = work / "aligned.jsonl"
    wall = run_aligner(exe, requests, aligned)
    summary = evaluate(work, aligned)
    summary["wall_align_s"] = wall
    write_reports(summary, work / "reports")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description="Laboratório sintético do alinhador Ler sem Barreiras")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--count", type=int, default=500)
    parser.add_argument("--twice", action="store_true", help="corre duas vezes e exige o mesmo fingerprint")
    parser.add_argument("--work", type=Path, default=LAB / "work")
    args = parser.parse_args()
    exe = gradle_install()
    first_dir = args.work / "run-a"
    summary_a = run_once(first_dir, args.seed, args.count, exe)
    fp_a = fingerprint(summary_a)
    print(f"Run A: {summary_a['count']} casos, alinhadas {summary_a['aligned_pct']:.2f}%, fingerprint {fp_a}")
    if args.twice:
        summary_b = run_once(args.work / "run-b", args.seed, args.count, exe)
        fp_b = fingerprint(summary_b)
        print(f"Run B: {summary_b['count']} casos, alinhadas {summary_b['aligned_pct']:.2f}%, fingerprint {fp_b}")
        if fp_a != fp_b:
            print("FALHA: os dois ensaios com a mesma seed não reproduziram os alinhamentos.", file=sys.stderr)
            return 2
        print("Reproduzível: os dois ensaios coincidem nos timestamps alinhados.")
    example = ROOT / "docs" / "test-lab"
    example.mkdir(parents=True, exist_ok=True)
    slim = {key: summary_a[key] for key in summary_a if key not in {"cases", "fingerprint"}}
    slim["worst"] = [
        {k: item[k] for k in ("id", "category", "missing", "mean_abs_error_ms", "text") if k in item}
        for item in summary_a["worst"][:10]
    ]
    (example / "example-summary.json").write_text(json.dumps(slim, ensure_ascii=False, indent=2), encoding="utf-8")
    (example / "example-report.html").write_text(
        (first_dir / "reports" / "report.html").read_text(encoding="utf-8"),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
