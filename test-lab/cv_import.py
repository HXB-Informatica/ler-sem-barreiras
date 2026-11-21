"""Importador de Mozilla Common Voice (áudio + transcrição).

Não versiona gravações. Não guarda identificadores de locutor, idade nem género.
O client_id original é re-hasheado só para separar desenvolvimento/avaliação.
"""

from __future__ import annotations

import csv
import hashlib
import io
import random
import urllib.request
from pathlib import Path

CV_LICENSE = "CC-0 (Mozilla Common Voice); as gravações não entram no Git"
DISCLAIMER_SPEAKERS = "Os relatórios usam locutor_anon (hash); não se tenta identificar pessoas."

PT_PT_MARKERS = (
    "pt-pt",
    "portugal",
    "lisboa",
    "lisboeta",
    "sotaque lisboeta",
    "porto",
    "coimbra",
    "minho",
    "alentejo",
    "algarve",
    "madeira",
    "açores",
    "acores",
    "português de portugal",
    "portugues de portugal",
    "português europeu",
    "portugues europeu",
    "european portuguese",
    "beira",
    "setúbal",
    "setubal",
    "braga",
    "aveiro",
    "galiza",
)
PT_BR_MARKERS = (
    "pt-br",
    "brasil",
    "brazil",
    "brazilian",
    "brasileiro",
    "brasileira",
    "carioca",
    "paulista",
    "nordestino",
    "nordestina",
    "mineiro",
    "mineira",
    "gaucho",
    "gaúcho",
    "baiano",
    "baiana",
    "fluminense",
    "capixaba",
    "piauí",
    "piaui",
    "potiguar",
    "paranaense",
    "gaúcha",
)
# sotaques que não são português europeu identificado
NON_PT_PT = (
    "galego",
    "français",
    "frances",
    "nativo de inglês",
    "ingles",
    "english",
    "spanish",
    "espanhol",
)

HF_PT_PARQUET = (
    "https://huggingface.co/datasets/remynd/common_voice_pt_pt/resolve/main/"
    "data/train-00000-of-00001.parquet"
)


def _norm(text: str) -> str:
    return (text or "").strip().lower().replace("_", "-")


def is_european_portuguese(row: dict) -> bool:
    variant = _norm(str(row.get("variant") or ""))
    if variant in {"pt-pt", "ptpt"}:
        return True
    if variant in {"pt-br", "ptbr"}:
        return False
    accents = _norm(str(row.get("accents") or row.get("accent") or ""))
    if not accents:
        return False
    if any(marker in accents for marker in PT_BR_MARKERS):
        return False
    if any(marker in accents for marker in NON_PT_PT):
        return False
    if accents in {"porto"} or accents.startswith("porto,") or accents.endswith(" porto"):
        if "alegre" in accents:
            return False
    return any(marker in accents for marker in PT_PT_MARKERS)


def anonymize_speaker(client_id: str) -> str:
    digest = hashlib.sha256(f"lsb-lab|{client_id}".encode("utf-8")).hexdigest()
    return digest[:16]


def split_for_speaker(locutor_anon: str) -> str:
    bucket = int(locutor_anon[:8], 16) % 100
    return "dev" if bucket < 70 else "eval"


def tokenize_words(text: str) -> list[str]:
    return [part for part in text.replace("\n", " ").split() if part]


def _read_tsv(path: Path) -> list[dict]:
    rows = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for raw in reader:
            rows.append({k: (v if v is not None else "") for k, v in raw.items()})
    return rows


def load_common_voice_dir(root: Path) -> list[dict]:
    """Lê validated.tsv / train.tsv e localiza clips/ no layout oficial do Common Voice."""
    tsv_candidates = [
        root / "validated.tsv",
        root / "train.tsv",
        root / "pt" / "validated.tsv",
        root / "pt" / "train.tsv",
    ]
    tsv = next((path for path in tsv_candidates if path.is_file()), None)
    if tsv is None:
        found = list(root.rglob("validated.tsv")) + list(root.rglob("train.tsv"))
        tsv = found[0] if found else None
    if tsv is None:
        raise FileNotFoundError(f"Não há validated.tsv/train.tsv em {root}")
    clip_roots = [
        tsv.parent / "clips",
        root / "clips",
        root / "pt" / "clips",
    ]
    clips = next((path for path in clip_roots if path.is_dir()), tsv.parent / "clips")
    out = []
    for row in _read_tsv(tsv):
        rel = row.get("path") or ""
        audio = clips / rel
        if not audio.is_file():
            audio = clips / Path(rel).name
        if not audio.is_file():
            continue
        out.append(
            {
                "variant": row.get("variant") or "",
                "accents": row.get("accents") or row.get("accent") or "",
                "sentence": row.get("sentence") or "",
                "audio_path": audio,
                "client_id": row.get("client_id") or rel,
                "locale": row.get("locale") or "pt",
            }
        )
    return out


def fetch_parquet(dest: Path, url: str = HF_PT_PARQUET) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_file() and dest.stat().st_size > 1_000_000:
        return dest
    request = urllib.request.Request(url, headers={"User-Agent": "ler-sem-barreiras-test-lab"})
    with urllib.request.urlopen(request, timeout=180) as response:
        dest.write_bytes(response.read())
    return dest


def load_parquet(path: Path) -> list[dict]:
    import pyarrow.parquet as pq

    table = pq.read_table(path)
    rows = []
    for item in table.to_pylist():
        audio = item.get("audio")
        payload = None
        name = "clip.mp3"
        if isinstance(audio, dict):
            payload = audio.get("bytes")
            name = Path(str(audio.get("path") or name)).name
        elif audio is not None:
            payload = audio
        if not payload:
            continue
        rows.append(
            {
                "variant": item.get("variant") or "",
                "accents": item.get("accents") or item.get("accent") or "",
                "sentence": item.get("sentence") or "",
                "audio_bytes": payload,
                "audio_name": name,
                "client_id": item.get("client_id") or name,
                "duration_ms": item.get("duration[ms]") or item.get("duration_ms") or 0,
                "locale": "pt",
            }
        )
    return rows


def materialize_row(row: dict, raw_dir: Path, index: int) -> Path:
    raw_dir.mkdir(parents=True, exist_ok=True)
    if row.get("audio_path"):
        return Path(row["audio_path"])
    name = row.get("audio_name") or f"clip-{index}.mp3"
    suffix = Path(name).suffix or ".mp3"
    target = raw_dir / f"{index:05d}{suffix}"
    target.write_bytes(row["audio_bytes"])
    return target


def select_sample(rows: list[dict], seed: int, count: int) -> tuple[list[dict], dict]:
    eligible = [row for row in rows if is_european_portuguese(row) and (row.get("sentence") or "").strip()]
    stats = {
        "source_rows": len(rows),
        "pt_pt_eligible": len(eligible),
        "requested": count,
        "license": CV_LICENSE,
        "speakers_note": DISCLAIMER_SPEAKERS,
    }
    rng = random.Random(seed)
    keyed = []
    for row in eligible:
        locutor = anonymize_speaker(str(row.get("client_id") or ""))
        keyed.append((locutor, rng.random(), row))
    keyed.sort(key=lambda item: (item[0], item[1]))
    speakers: dict[str, list[dict]] = {}
    for locutor, _, row in keyed:
        speakers.setdefault(locutor, []).append(row)
    speaker_ids = list(speakers)
    rng.shuffle(speaker_ids)
    chosen: list[dict] = []
    for locutor in speaker_ids:
        split = split_for_speaker(locutor)
        for row in speakers[locutor]:
            if len(chosen) >= count:
                break
            source = {key: value for key, value in row.items() if key != "client_id"}
            chosen.append(
                {
                    "sentence": row["sentence"].strip(),
                    "locutor_anon": locutor,
                    "split": split,
                    "source_row": source,
                    "pt_pt": True,
                }
            )
        if len(chosen) >= count:
            break
    stats["selected"] = len(chosen)
    stats["speakers"] = len({item["locutor_anon"] for item in chosen})
    stats["dev"] = sum(1 for item in chosen if item["split"] == "dev")
    stats["eval"] = sum(1 for item in chosen if item["split"] == "eval")
    stats["shortfall"] = max(0, count - len(chosen))
    return chosen, stats
