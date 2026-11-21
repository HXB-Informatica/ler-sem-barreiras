"""Frases em português europeu para o laboratório sintético."""

from __future__ import annotations

CANONICAL = "Olá um dois três"

PHRASES = [
    "Olá um dois três",
    "O gato corre",
    "A menina lê o livro",
    "Bom dia professora",
    "O sol brilha no céu",
    "A casa é amarela",
    "Um dois três quatro cinco",
    "Seis sete oito nove dez",
    "O a e de em um",
    "o gato o gato",
    "a bola a bola a bola",
    "Olá, mundo!",
    "Sim, eu sei.",
    "Lê, escreve e conta.",
    "O pão e o leite",
    "A árvore tem folhas",
    "Os miúdos brincam no recreio",
    "A professora lê em voz alta",
    "O cão late ao longe",
    "Há três livros na mesa",
    "Um e um são dois",
    "Vamos ler sem barreiras",
    "A frase é curta",
    "Repete a palavra palavra",
    "O rio Tejo passa em Lisboa",
]

SHORT = ["o", "a", "e", "de", "em", "um", "às", "no", "na"]
NUMBERS = ["um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez"]
NOUNS = ["gato", "casa", "bola", "livro", "mesa", "sol", "pão", "rio", "flor", "porta"]
VERBS = ["corre", "lê", "salta", "canta", "olha", "come", "abre", "fecha"]

CATEGORIES = [
    "canonical",
    "short_words",
    "numbers",
    "punctuation",
    "repeats",
    "pause_long",
    "speed_fast",
    "speed_slow",
    "noise",
    "asr_omit",
    "asr_substitute",
    "asr_extra",
    "paragraph",
]


def phrase_for(index: int, category: str, rng) -> str:
    if category == "canonical" or index == 0:
        return CANONICAL
    if category == "short_words":
        n = 3 + (index % 4)
        return " ".join(rng.sample(SHORT, k=min(n, len(SHORT))))
    if category == "numbers":
        n = 3 + (index % 6)
        return " ".join(NUMBERS[:n] if index % 2 == 0 else list(reversed(NUMBERS[:n])))
    if category == "punctuation":
        base = rng.choice(["Olá", "Sim", "Bom dia", "Atenção"])
        return f"{base}, {rng.choice(NOUNS)}!"
    if category == "repeats":
        word = rng.choice(NOUNS + SHORT)
        times = 2 + (index % 3)
        return " ".join([word] * times)
    if category == "paragraph":
        a = rng.choice(PHRASES)
        b = rng.choice(PHRASES)
        return f"{a}.\n\n{b}."
    if category in {"asr_omit", "asr_substitute", "asr_extra", "pause_long", "speed_fast", "speed_slow", "noise"}:
        return rng.choice(PHRASES)
    return rng.choice(PHRASES)
