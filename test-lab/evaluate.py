from __future__ import annotations

import json
import statistics
from pathlib import Path

from memory import memory_note


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


def evaluate(out_dir: Path, aligned_path: Path) -> dict:
    catalog = json.loads((out_dir / "catalog.json").read_text(encoding="utf-8"))
    by_id = {case["id"]: case for case in catalog["cases"]}
    rows = []
    start_errors: list[float] = []
    end_errors: list[float] = []
    by_category: dict[str, list[dict]] = {}
    for line in aligned_path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        pred = json.loads(line)
        case = by_id[pred["id"]]
        ref = {item["index"]: item for item in case["words"]}
        word_rows = []
        missing = 0
        invalid = 0
        abs_errs = []
        for word in pred["words"]:
            expected = ref.get(word["index"])
            start = word.get("startMs")
            end = word.get("endMs")
            if start is None or end is None or expected is None:
                missing += 1
                word_rows.append({"text": word["text"], "index": word["index"], "missing": True})
                continue
            if end < start:
                invalid += 1
            ds = abs(start - expected["start_ms"])
            de = abs(end - expected["end_ms"])
            start_errors.append(ds)
            end_errors.append(de)
            abs_errs.append((ds + de) / 2)
            word_rows.append(
                {
                    "text": word["text"],
                    "index": word["index"],
                    "start_error_ms": ds,
                    "end_error_ms": de,
                    "state": word.get("state"),
                }
            )
        issues = pred.get("issues") or []
        invalid += sum(1 for issue in issues if issue.get("type") in {"OVERLAP", "INVALID_ORDER", "ZERO_LENGTH"})
        total = len(case["words"])
        aligned = total - missing
        row = {
            "id": case["id"],
            "category": case["category"],
            "text": case["text"],
            "word_count": total,
            "aligned": aligned,
            "aligned_pct": 100.0 * aligned / total if total else 0.0,
            "missing": missing,
            "invalid_intervals": invalid,
            "mean_abs_error_ms": statistics.mean(abs_errs) if abs_errs else None,
            "elapsed_ms": pred.get("elapsedMs"),
            "words": word_rows,
        }
        rows.append(row)
        by_category.setdefault(case["category"], []).append(row)

    def summarize(items: list[dict]) -> dict:
        words_total = sum(item["word_count"] for item in items)
        words_aligned = sum(item["aligned"] for item in items)
        errs = [item["mean_abs_error_ms"] for item in items if item["mean_abs_error_ms"] is not None]
        return {
            "cases": len(items),
            "words": words_total,
            "aligned_pct": 100.0 * words_aligned / words_total if words_total else 0.0,
            "unrecognized_words": sum(item["missing"] for item in items),
            "invalid_intervals": sum(item["invalid_intervals"] for item in items),
            "mean_error_ms": statistics.mean(errs) if errs else 0.0,
            "median_error_ms": statistics.median(errs) if errs else 0.0,
            "p95_error_ms": percentile(errs, 95) if errs else 0.0,
            "mean_processing_ms": statistics.mean([item["elapsed_ms"] for item in items if item["elapsed_ms"] is not None] or [0.0]),
        }

    combined = start_errors + end_errors
    summary = {
        "seed": catalog["seed"],
        "count": len(rows),
        "disclaimer": catalog["disclaimer"],
        "reference_kind": "synthetic_concat_pcm",
        "memory": memory_note(),
        "aligned_pct": summarize(rows)["aligned_pct"],
        "unrecognized_words": summarize(rows)["unrecognized_words"],
        "invalid_intervals": summarize(rows)["invalid_intervals"],
        "mean_error_ms": statistics.mean(combined) if combined else 0.0,
        "median_error_ms": statistics.median(combined) if combined else 0.0,
        "p95_error_ms": percentile(combined, 95) if combined else 0.0,
        "mean_processing_ms": summarize(rows)["mean_processing_ms"],
        "by_category": {name: summarize(items) for name, items in sorted(by_category.items())},
        "worst": sorted(
            rows,
            key=lambda item: (
                item["missing"],
                item["mean_abs_error_ms"] if item["mean_abs_error_ms"] is not None else 10_000,
            ),
            reverse=True,
        )[:25],
        "fingerprint": [
            {
                "id": item["id"],
                "aligned": item["aligned"],
                "missing": item["missing"],
                "mean_abs_error_ms": item["mean_abs_error_ms"],
            }
            for item in rows
        ],
        "cases": rows,
    }
    return summary
