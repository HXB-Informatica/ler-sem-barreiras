"""Relatório HTML/JSON/CSV do laboratório de vozes reais."""

from __future__ import annotations

import csv
import json
from pathlib import Path

from classify import DISCLAIMER


def write_reports(summary: dict, reports: Path) -> None:
    reports.mkdir(parents=True, exist_ok=True)
    (reports / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    with (reports / "cases.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "id",
                "split",
                "primary",
                "mapped_a",
                "mapped_b",
                "word_count",
                "median_divergence_ms",
                "missing_a",
                "elapsed_a_ms",
                "elapsed_b_ms",
                "wav",
                "text",
            ],
        )
        writer.writeheader()
        for row in summary["cases"]:
            writer.writerow(
                {
                    "id": row["id"],
                    "split": row["split"],
                    "primary": row["classification"]["primary"],
                    "mapped_a": row["comparison"]["mapped_a"],
                    "mapped_b": row["comparison"]["mapped_b"],
                    "word_count": row["comparison"]["word_count"],
                    "median_divergence_ms": row["comparison"]["median_divergence_ms"],
                    "missing_a": len(row["comparison"]["missing_a"]),
                    "elapsed_a_ms": row.get("elapsed_a_ms"),
                    "elapsed_b_ms": row.get("elapsed_b_ms"),
                    "wav": row.get("wav_rel"),
                    "text": row["text"],
                }
            )
    (reports / "dashboard.html").write_text(_html(summary), encoding="utf-8")
    (reports / "problemas-alinhador.md").write_text(_findings(summary), encoding="utf-8")


def _html(summary: dict) -> str:
    dist = "".join(
        f"<tr><td>{name}</td><td>{count}</td></tr>" for name, count in summary["label_counts"].items()
    )
    critical = "".join(
        f"<tr><td>{row['id']}</td><td>{row['split']}</td><td>{row['classification']['primary']}</td>"
        f"<td>{row['comparison']['median_divergence_ms']}</td>"
        f"<td><a href=\"{row.get('wav_rel', '')}\">abrir áudio</a></td>"
        f"<td>{row['text']}</td></tr>"
        for row in summary["critical"]
    )
    regression = summary.get("regression") or {}
    reg_rows = "".join(
        f"<tr><td>{key}</td><td>{value}</td></tr>"
        for key, value in regression.items()
        if key != "label_delta"
    )
    history = "".join(
        f"<li><code>{item}</code></li>" for item in summary.get("history_files") or []
    )
    return f"""<!DOCTYPE html>
<html lang="pt">
<head><meta charset="utf-8"><title>Vozes reais — Ler sem Barreiras</title>
<style>
body {{ font-family: sans-serif; max-width: 1100px; margin: 2rem auto; background: #F4F1EA; color: #1B1B1B; }}
.warn {{ background: #FFE2E0; padding: 1rem; border: 1px solid #9B1C1C; }}
table {{ border-collapse: collapse; width: 100%; background: white; margin: 1rem 0; }}
td, th {{ border: 1px solid #ccc; padding: 6px 8px; text-align: left; }}
</style></head>
<body>
<h1>Laboratório de vozes reais e comparação de alinhadores</h1>
<p class="warn">{DISCLAIMER}</p>
<p>Gravações: {summary["count"]} · Palavras: {summary["words"]} ·
mapeadas motor actual: {summary["mapped_pct_a"]:.1f}% · segundo motor: {summary["mapped_pct_b"]:.1f}% ·
dev {summary.get("dev_count", 0)} / eval {summary.get("eval_count", 0)}</p>
<p>Segundo motor: {summary.get("second_engine")} · Processamento médio actual: {summary.get("mean_elapsed_a_ms", 0):.1f} ms ·
segundo: {summary.get("mean_elapsed_b_ms", 0):.1f} ms</p>
<p>Licença dos dados: {summary.get("license")}</p>
<h2>Distribuição das classificações</h2>
<table><tr><th>Classe</th><th>Casos</th></tr>{dist}</table>
<h2>Casos críticos (abrir só os problemáticos)</h2>
<table><tr><th>id</th><th>split</th><th>classe</th><th>divergência mediana</th><th>áudio</th><th>texto</th></tr>
{critical}</table>
<h2>Comparação com a execução anterior</h2>
<table><tr><th>métrica</th><th>valor</th></tr>{reg_rows or "<tr><td colspan=2>Sem histórico anterior.</td></tr>"}</table>
<h2>Histórico de regressões</h2>
<ul>{history or "<li>Nenhum ficheiro de histórico ainda.</li>"}</ul>
</body></html>
"""


def _findings(summary: dict) -> str:
    missing = summary.get("missing_words_a", 0)
    invalid = summary.get("invalid_a", 0)
    overlaps = summary.get("overlaps_a", 0)
    high = summary["label_counts"].get("divergencia_elevada", 0)
    fail = summary["label_counts"].get("falha_tecnica", 0)
    mismatch = summary["label_counts"].get("incompatibilidade_texto_audio", 0)
    agree = summary["label_counts"].get("concordancia_elevada", 0)
    return f"""# Problemas encontrados no alinhador actual

Este texto resulta da validação cruzada (alinhador da aplicação vs segundo motor + VAD).
**Não** é uma taxa de precisão absoluta.

## Síntese

- Palavras do texto sem timestamp no motor actual: {missing}
- Intervalos inválidos (motor actual): {invalid} (sobreposições: {overlaps})
- Casos de divergência elevada face ao segundo motor: {high}
- Falhas técnicas: {fail}
- Possível incompatibilidade texto/áudio (VAD): {mismatch}
- Concordância elevada (não prova correcção): {agree} / {summary["count"]}

## Interpretação fundamentada

1. O alinhador recusa inventar tempos quando o reconhecimento não corresponde ao texto (Needleman–Wunsch). As `palavra_ausente` são o comportamento esperado em omissões, mas no produto exigem revisão do professor.
2. Divergências face ao alinhador de energia/MFA concentram-se em pausas e palavras curtas. Nenhuma das duas escalas é fonética de referência, salvo MFA com modelo português validado.
3. O sinal mais frequente neste ensaio é **sobreposição de intervalos**: timestamps de palavras consecutivas partilham o mesmo instante. O editor de revisão continua a ser necessário.
4. Palavras do motor actual sobrepostas a silêncio (VAD) sugerem fronteiras deslocadas para pausas.
5. Falhas técnicas isoladas não abortam a bateria; cada `id` reproduz o caso.

## O que isto não mede

Sem alinhamento manual ou MFA português instalado, **não estimamos precisão real em milissegundos**.
"""
