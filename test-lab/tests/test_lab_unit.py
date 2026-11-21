import json
import random
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from audio import concatenate_sentence, samples_to_ms, word_duration_ms  # noqa: E402
from evaluate import percentile  # noqa: E402
from generate import generate_cases, tokenize  # noqa: E402
from phrases import CANONICAL  # noqa: E402


class LabUnitTests(unittest.TestCase):
    def test_pcm_timestamps_match_sample_counts(self):
        rng = random.Random(1)
        words = ["Olá", "um", "dois", "três"]
        pcm, timed, duration = concatenate_sentence(words, 1.0, 50, 0.0, rng)
        self.assertEqual(duration, samples_to_ms(len(pcm) // 2))
        self.assertEqual(4, len(timed))
        self.assertEqual(0, timed[0]["start_ms"])
        for left, right in zip(timed, timed[1:]):
            self.assertLess(left["end_ms"], right["start_ms"])
            self.assertGreaterEqual(right["start_ms"] - left["end_ms"], 40)

    def test_word_duration_scales_with_speed(self):
        slow = word_duration_ms("gato", 1.6)
        fast = word_duration_ms("gato", 0.6)
        self.assertGreater(slow, fast)

    def test_tokenize_matches_whitespace_words(self):
        self.assertEqual(["Olá", "um", "dois", "três"], tokenize(CANONICAL))
        self.assertEqual(["Olá,", "mundo!"], tokenize("Olá, mundo!"))

    def test_percentile(self):
        self.assertEqual(5, percentile([1, 3, 5, 7, 9], 50))

    def test_generate_is_deterministic(self):
        import tempfile

        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            a = generate_cases(Path(first), seed=7, count=8)
            b = generate_cases(Path(second), seed=7, count=8)
            self.assertEqual(a.read_text(encoding="utf-8"), b.read_text(encoding="utf-8"))
            catalog = json.loads((Path(first) / "catalog.json").read_text(encoding="utf-8"))
            self.assertEqual("Olá um dois três", catalog["cases"][0]["text"])
            self.assertIn("não fronteiras fonéticas", catalog["disclaimer"])


if __name__ == "__main__":
    unittest.main()
