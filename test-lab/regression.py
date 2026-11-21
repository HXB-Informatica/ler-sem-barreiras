"""Comparação com execuções anteriores do laboratório (regressão)."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path


def fingerprint(rows: list[dict]) -> str:
    import hashlib

    payload = [
        {
            "id": row["id"],
            "primary": row["classification"]["primary"],
            "mapped_a": row["comparison"]["mapped_a"],
            "mapped_b": row["comparison"]["mapped_b"],
            "median_divergence_ms": row["comparison"]["median_divergence_ms"],
        }
        for row in rows
    ]
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()


def save_history(history_dir: Path, summary: dict) -> Path:
    history_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = history_dir / f"{stamp}.json"
    slim = {key: summary[key] for key in summary if key not in {"cases"}}
    slim["fingerprint"] = summary.get("fingerprint")
    path.write_text(json.dumps(slim, ensure_ascii=False, indent=2), encoding="utf-8")
    latest = history_dir / "latest.json"
    latest.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")
    return path


def load_previous(history_dir: Path, current_path: Path | None = None) -> dict | None:
    files = sorted(history_dir.glob("20*.json")) if history_dir.is_dir() else []
    if current_path:
        files = [path for path in files if path.resolve() != current_path.resolve()]
    if not files:
        latest = history_dir / "latest.json"
        if latest.is_file() and (current_path is None or latest.resolve() != current_path.resolve()):
            return json.loads(latest.read_text(encoding="utf-8"))
        return None
    return json.loads(files[-1].read_text(encoding="utf-8"))


def diff_runs(previous: dict, current: dict) -> dict:
    prev_labels = previous.get("label_counts") or {}
    curr_labels = current.get("label_counts") or {}
    keys = sorted(set(prev_labels) | set(curr_labels))
    label_delta = {key: curr_labels.get(key, 0) - prev_labels.get(key, 0) for key in keys}
    return {
        "previous_fingerprint": previous.get("fingerprint"),
        "current_fingerprint": current.get("fingerprint"),
        "mapped_pct_a_delta": current.get("mapped_pct_a", 0) - previous.get("mapped_pct_a", 0),
        "mapped_pct_b_delta": current.get("mapped_pct_b", 0) - previous.get("mapped_pct_b", 0),
        "critical_delta": current.get("critical_count", 0) - previous.get("critical_count", 0),
        "label_delta": label_delta,
        "same_fingerprint": previous.get("fingerprint") == current.get("fingerprint"),
        "note": "Diferenças de classificação entre versões/ensaios; não medem precisão absoluta.",
    }
