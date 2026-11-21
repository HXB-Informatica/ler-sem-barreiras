import json
import random
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from audio import concatenate_sentence, write_wav  # noqa: E402
from classify import classify_case  # noqa: E402
from compare import compare_case  # noqa: E402
from cv_import import anonymize_speaker, is_european_portuguese, select_sample, split_for_speaker  # noqa: E402
from regression import diff_runs, fingerprint  # noqa: E402
from second_aligner import energy_dtw_align, parse_textgrid_words  # noqa: E402
from vad import speech_intervals_ms  # noqa: E402


class RealVoiceTests(unittest.TestCase):
    def test_filters_european_portuguese_metadata(self):
        self.assertTrue(is_european_portuguese({"variant": "pt-PT", "accents": ""}))
        self.assertTrue(is_european_portuguese({"variant": "", "accents": "Sotaque Lisboeta"}))
        self.assertTrue(is_european_portuguese({"accents": "Porto"}))
        self.assertTrue(is_european_portuguese({"accents": "Zona limítrofe com a Galiza"}))
        self.assertFalse(is_european_portuguese({"variant": "pt-BR"}))
        self.assertFalse(is_european_portuguese({"accents": "Nordestino"}))
        self.assertFalse(is_european_portuguese({"accents": "Brazilian"}))
        self.assertFalse(is_european_portuguese({"accents": "galego"}))
        self.assertFalse(is_european_portuguese({"accents": ""}))

    def test_speaker_hash_is_stable_and_not_raw_id(self):
        raw = "abc123client"
        anon = anonymize_speaker(raw)
        self.assertEqual(16, len(anon))
        self.assertNotEqual(raw, anon)
        self.assertEqual(anon, anonymize_speaker(raw))
        self.assertIn(split_for_speaker(anon), {"dev", "eval"})

    def test_select_sample_splits_by_speaker(self):
        rows = []
        for i in range(30):
            rows.append(
                {
                    "variant": "pt-PT",
                    "accents": "Lisboeta",
                    "sentence": f"Olá mundo {i}",
                    "client_id": "speaker-a" if i < 20 else "speaker-b",
                }
            )
        chosen, stats = select_sample(rows, seed=8, count=10)
        self.assertEqual(10, len(chosen))
        self.assertGreaterEqual(stats["speakers"], 1)
        by_speaker = {}
        for item in chosen:
            by_speaker.setdefault(item["locutor_anon"], set()).add(item["split"])
        for splits in by_speaker.values():
            self.assertEqual(1, len(splits))
        dumped = json.dumps(chosen)
        self.assertNotIn("speaker-a", dumped)
        self.assertNotIn("client_id", dumped)

    def test_vad_detects_tone_not_silence(self):
        rng = random.Random(1)
        pcm, _, _ = concatenate_sentence(["Olá", "mundo"], 1.0, 80, 0.0, rng)
        spoken = speech_intervals_ms(pcm, 16_000)
        self.assertTrue(spoken)
        silence = b"\x00\x00" * 16_000
        self.assertEqual([], speech_intervals_ms(silence, 16_000))

    def test_energy_aligner_covers_all_tokens(self):
        rng = random.Random(2)
        pcm, _, duration = concatenate_sentence(["um", "dois", "três"], 1.0, 40, 0.0, rng)
        words = energy_dtw_align(pcm, 16_000, "um dois três")
        self.assertEqual(3, len(words))
        self.assertTrue(all(w["startMs"] is not None for w in words))
        self.assertLessEqual(words[-1]["endMs"], duration + 40)

    def test_compare_and_classify_missing_and_invalid(self):
        rng = random.Random(4)
        pcm, _, duration = concatenate_sentence(["Olá"], 1.0, 30, 0.0, rng)
        a = [{"text": "Olá", "index": 0, "startMs": None, "endMs": None}]
        b = [{"text": "Olá", "index": 0, "startMs": 10, "endMs": 5}]
        comparison = compare_case("Olá", duration, pcm, 16_000, a, b)
        self.assertEqual([0], comparison["missing_a"])
        self.assertTrue(comparison["invalid_b"])
        labels = classify_case(comparison, None, None)
        self.assertEqual("intervalo_invalido", labels["primary"])
        fail = classify_case(comparison, "motor em baixo", None)
        self.assertEqual("falha_tecnica", fail["primary"])

    def test_technical_failure_does_not_use_agreement_label(self):
        comparison = {
            "missing_a": [],
            "missing_b": [],
            "invalid_a": [],
            "invalid_b": [],
            "median_divergence_ms": 10,
            "possible_mismatch": False,
        }
        self.assertEqual("falha_tecnica", classify_case(comparison, "boom", None)["primary"])

    def test_textgrid_parser(self):
        grid = """
        item [1]:
        class = "IntervalTier"
        name = "words"
        intervals [1]:
        xmin = 0.0
        xmax = 0.2
        text = ""
        intervals [2]:
        xmin = 0.2
        xmax = 0.5
        text = "olá"
        """
        parsed = parse_textgrid_words(grid)
        self.assertEqual("olá", parsed[0]["text"])
        self.assertEqual(200, parsed[0]["startMs"])

    def test_regression_diff_and_disclaimer(self):
        row = {
            "id": "x",
            "classification": {"primary": "palavra_ausente"},
            "comparison": {"mapped_a": 1, "mapped_b": 2, "median_divergence_ms": 40},
        }
        fp = fingerprint([row])
        prev = {"fingerprint": fp, "mapped_pct_a": 90, "mapped_pct_b": 100, "critical_count": 2, "label_counts": {"palavra_ausente": 2}}
        curr = {"fingerprint": "other", "mapped_pct_a": 80, "mapped_pct_b": 100, "critical_count": 3, "label_counts": {"palavra_ausente": 3}}
        diff = diff_runs(prev, curr)
        self.assertEqual(-10, diff["mapped_pct_a_delta"])
        self.assertFalse(diff["same_fingerprint"])
        self.assertIn("precis", diff["note"].lower())

    def test_pipeline_survives_one_bad_clip(self):
        rng = random.Random(3)
        with tempfile.TemporaryDirectory() as tmp:
            pcm, _, _ = concatenate_sentence(["Olá"], 1.0, 30, 0.0, rng)
            path = Path(tmp) / "ok.wav"
            write_wav(path, pcm)
            self.assertTrue(path.is_file())
        comparison = compare_case("", 0, b"", 16000, [], [])
        result = classify_case(comparison, "falha isolada", None)
        self.assertEqual("falha_tecnica", result["primary"])


if __name__ == "__main__":
    unittest.main()
