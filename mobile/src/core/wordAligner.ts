import { fillGaps } from './gapFiller';
import type { AlignmentResult, RecognizedWord, TextToken, WordTiming } from './models';
import { isTimed, requiresReview } from './models';
import { prepareRecognized } from './recognizedPreprocessor';
import { inspectIntervals } from './timeValidator';
import { levenshtein, normalizeForMatch, tokenize } from './tokenizer';
import { fromToken, unmatched } from './wordIdentity';

const DIAG = 1;
const UP = 2;
const LEFT = 3;
export const MATCH_SCORE = 4;
export const MISMATCH_SCORE = -2;
export const GAP_SCORE = -1;
export const MIN_CONFIDENCE = 0.35;

export function similarity(source: string, recognized: string): number {
  if (source.length === 0 || recognized.length === 0) {
    return MISMATCH_SCORE;
  }
  if (source === recognized) {
    return MATCH_SCORE;
  }
  const distance = levenshtein(source, recognized);
  const longest = Math.max(source.length, recognized.length);
  const shortest = Math.min(source.length, recognized.length);
  if (distance === 1 && longest >= 3) {
    return MATCH_SCORE - 1;
  }
  if (distance === 2 && longest >= 6) {
    return 1;
  }
  if (shortest >= 4 && (source.startsWith(recognized) || recognized.startsWith(source))) {
    return 1;
  }
  return MISMATCH_SCORE;
}

export function alignTokens(
  source: TextToken[],
  recognized: RecognizedWord[],
  audioDurationMs: number,
): AlignmentResult {
  if (source.length === 0) {
    return {
      words: [],
      alignedCount: 0,
      reviewCount: 0,
      unusedRecognizedCount: recognized.length,
      intervalIssues: [],
    };
  }

  const prepared = prepareRecognized(source, recognized);
  const normalizedRecognized = prepared.map(word => ({
    ...word,
    text: normalizeForMatch(word.text),
  }));

  const n = source.length;
  const m = normalizedRecognized.length;
  const score: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  const pointer: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));

  for (let i = 1; i <= n; i++) {
    score[i][0] = i * GAP_SCORE;
    pointer[i][0] = UP;
  }
  for (let j = 1; j <= m; j++) {
    score[0][j] = j * GAP_SCORE;
    pointer[0][j] = LEFT;
  }

  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      const diagonal = score[i - 1][j - 1] + similarity(source[i - 1].normalized, normalizedRecognized[j - 1].text);
      const up = score[i - 1][j] + GAP_SCORE;
      const left = score[i][j - 1] + GAP_SCORE;
      if (diagonal >= up && diagonal >= left) {
        score[i][j] = diagonal;
        pointer[i][j] = DIAG;
      } else if (up >= left) {
        score[i][j] = up;
        pointer[i][j] = UP;
      } else {
        score[i][j] = left;
        pointer[i][j] = LEFT;
      }
    }
  }

  const pairs: Array<number | null> = Array.from({ length: n }, () => null);
  let i = n;
  let j = m;
  let unusedRecognized = 0;
  while (i > 0 || j > 0) {
    const direction = pointer[i][j];
    if (direction === DIAG) {
      const sim = similarity(source[i - 1].normalized, normalizedRecognized[j - 1].text);
      if (sim > 0) {
        pairs[i - 1] = j - 1;
      }
      i -= 1;
      j -= 1;
    } else if (direction === UP) {
      i -= 1;
    } else {
      unusedRecognized += 1;
      j -= 1;
    }
  }

  const words: WordTiming[] = source.map((token, index) => {
    const recognizedIndex = pairs[index];
    if (recognizedIndex == null) {
      return unmatched(token, index);
    }
    const rec = prepared[recognizedIndex];
    const sim = similarity(token.normalized, normalizedRecognized[recognizedIndex].text);
    const confidence = Math.min(1, Math.max(0, rec.probability * (sim / MATCH_SCORE)));
    const review = confidence < MIN_CONFIDENCE || sim < MATCH_SCORE;
    return fromToken({
      token,
      index,
      startMs: Math.max(0, rec.startMs),
      endMs: Math.max(rec.endMs, rec.startMs),
      confidence,
      state: review ? 'REVIEW' : 'AUTO',
      origin: 'ASR',
      needsReview: review,
    });
  });

  const filled = fillGaps(words);
  const validated = inspectIntervals(filled, audioDurationMs);
  return {
    words: validated.words,
    alignedCount: validated.words.filter(isTimed).length,
    reviewCount: validated.words.filter(requiresReview).length,
    unusedRecognizedCount: unusedRecognized,
    intervalIssues: validated.issues,
  };
}

export function alignText(
  sourceText: string,
  recognized: RecognizedWord[],
  audioDurationMs: number,
): AlignmentResult {
  return alignTokens(tokenize(sourceText), recognized, audioDurationMs);
}
