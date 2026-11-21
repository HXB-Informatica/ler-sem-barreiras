import { isProtectedFromResync, isTimed, type WordTiming } from './models';

export const MIN_MS_PER_WORD = 40;
export const MAX_MS_PER_WORD = 500;

export function fillGaps(words: WordTiming[]): WordTiming[] {
  if (words.length < 3) {
    return words;
  }
  const out = words.slice();
  let i = 0;
  while (i < out.length) {
    if (isTimed(out[i])) {
      i += 1;
      continue;
    }
    let j = i;
    while (j < out.length && !isTimed(out[j])) {
      j += 1;
    }
    const left = i > 0 ? out[i - 1] : undefined;
    const right = j < out.length ? out[j] : undefined;
    const gapStart = left?.endMs;
    const gapEnd = right?.startMs;
    const missing = j - i;
    if (
      left &&
      right &&
      isTimed(left) &&
      isTimed(right) &&
      !isProtectedFromResync(left) &&
      !isProtectedFromResync(right) &&
      gapStart != null &&
      gapEnd != null &&
      missing > 0
    ) {
      const gap = gapEnd - gapStart;
      if (gap >= missing * MIN_MS_PER_WORD && gap <= missing * MAX_MS_PER_WORD) {
        const weights = [];
        for (let idx = i; idx < j; idx++) {
          weights.push(Math.max(2, countLettersOrDigits(out[idx].text)));
        }
        const total = Math.max(
          1,
          weights.reduce((sum, value) => sum + value, 0),
        );
        let cursor = gapStart;
        for (let offset = 0; offset < missing; offset++) {
          const idx = i + offset;
          if (isProtectedFromResync(out[idx])) {
            continue;
          }
          const span = Math.max(MIN_MS_PER_WORD, Math.floor((gap * weights[offset]) / total));
          const end = offset === missing - 1 ? gapEnd : Math.min(cursor + span, gapEnd);
          const stop = Math.max(end, cursor + 20);
          out[idx] = {
            ...out[idx],
            startMs: cursor,
            endMs: stop,
            confidence: 0.25,
            needsReview: true,
            state: 'REVIEW',
            timestampOrigin: 'ALIGNED',
          };
          cursor = stop;
        }
      }
    }
    i = j;
  }
  return out;
}

function countLettersOrDigits(text: string): number {
  let count = 0;
  for (const char of text) {
    if (/\p{L}|\p{N}/u.test(char)) {
      count += 1;
    }
  }
  return count;
}
