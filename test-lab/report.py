from __future__ import annotations

import csv
import json
from pathlib import Path


def write_reports(summary: dict, reports: Path) -> None:
    reports.mkdir(parents=True, exist_ok=True)
    (reports / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    with (reports / "cases.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "id",
                "category",
                "text",
                "word_count",
                "aligned",
                "aligned_pct",
                "missing",
                "invalid_intervals",
                "mean_abs_error_ms",
                "elapsed_ms",
            ],
        )
        writer.writeheader()
        for row in summary["cases"]:
            writer.writerow({key: row.get(key) for key in writer.fieldnames})
    worst_rows = "".join(
        f"<tr><td>{item['id']}</td><td>{item['category']}</td><td>{item['missing']}</td>"
        f"<td>{item['mean_abs_error_ms']}</td><td>{item['text']}</td></tr>"
        for item in summary["worst"]
    )
    cat_rows = "".join(
        f"<tr><td>{name}</td><td>{vals['cases']}</td><td>{vals['aligned_pct']:.1f}%</td>"
        f"<td>{vals['mean_error_ms']:.1f}</td><td>{vals['p95_error_ms']:.1f}</td>"
        f"<td>{vals['unrecognized_words']}</td></tr>"
        for name, vals in summary["by_category"].items()
    )
    html = f"""<!DOCTYPE html>
<html lang="pt">
<head><meta charset="utf-8"><title>Laboratório Ler sem Barreiras</title>
<style>
body {{ font-family: sans-serif; max-width: 960px; margin: 2rem auto; background: #F4F1EA; color: #1B1B1B; }}
.warn {{ background: #FFE2E0; padding: 1rem; border: 1px solid #9B1C1C; }}
table {{ border-collapse: collapse; width: 100%; background: white; }}
td, th {{ border: 1px solid #ccc; padding: 6px 8px; text-align: left; }}
</style></head>
<body>
<h1>Laboratório de testes sintéticos</h1>
<p class="warn">{summary["disclaimer"]}</p>
<p>Casos: {summary["count"]} · Palavras alinhadas: {summary["aligned_pct"]:.1f}% ·
erro médio: {summary["mean_error_ms"]:.1f} ms · mediana: {summary["median_error_ms"]:.1f} ms ·
P95: {summary["p95_error_ms"]:.1f} ms · não reconhecidas: {summary["unrecognized_words"]} ·
intervalos inválidos: {summary["invalid_intervals"]}</p>
<p>Memória: {summary["memory"]}</p>
<h2>Por categoria</h2>
<table><tr><th>Categoria</th><th>Casos</th><th>Alinhadas</th><th>Erro médio</th><th>P95</th><th>Não reconhecidas</th></tr>
{cat_rows}</table>
<h2>Piores casos</h2>
<table><tr><th>id</th><th>categoria</th><th>em falta</th><th>erro médio</th><th>texto</th></tr>
{worst_rows}</table>
</body></html>
"""
    (reports / "report.html").write_text(html, encoding="utf-8")
