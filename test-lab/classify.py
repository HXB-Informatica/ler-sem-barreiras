"""Classificação automática por prioridade de investigação.

A concordância entre motores não é prova de alinhamento correcto.
"""

from __future__ import annotations

LABELS = [
    "falha_tecnica",
    "incompatibilidade_texto_audio",
    "intervalo_invalido",
    "palavra_ausente",
    "divergencia_elevada",
    "divergencia_moderada",
    "concordancia_elevada",
]

PRIORITY = {name: index for index, name in enumerate(LABELS)}

AGREEMENT_MS = 80
MODERATE_MS = 250

DISCLAIMER = (
    "A concordância entre motores não prova que o alinhamento esteja correcto. "
    "A precisão temporal real só pode estimar-se com referências independentes "
    "e fiáveis (anotação humana ou alinhamento fonético validado). "
    "A ausência de erros detectados não é precisão comprovada."
)


def classify_case(comparison: dict, error_a: str | None, error_b: str | None) -> dict:
    labels = []
    if error_a or error_b:
        labels.append("falha_tecnica")
    if comparison.get("possible_mismatch"):
        labels.append("incompatibilidade_texto_audio")
    if comparison.get("invalid_a") or comparison.get("invalid_b"):
        labels.append("intervalo_invalido")
    if comparison.get("missing_a") or comparison.get("missing_b"):
        labels.append("palavra_ausente")
    median = comparison.get("median_divergence_ms")
    if median is None:
        if "palavra_ausente" not in labels and "falha_tecnica" not in labels:
            labels.append("divergencia_elevada")
    elif median >= MODERATE_MS:
        labels.append("divergencia_elevada")
    elif median >= AGREEMENT_MS:
        labels.append("divergencia_moderada")
    else:
        labels.append("concordancia_elevada")
    primary = min(labels, key=lambda name: PRIORITY[name])
    investigation = primary != "concordancia_elevada"
    return {
        "labels": labels,
        "primary": primary,
        "priority": PRIORITY[primary],
        "needs_investigation": investigation,
        "disclaimer": DISCLAIMER,
    }
