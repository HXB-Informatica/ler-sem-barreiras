import { isProtectedFromResync, isTimed, type IntervalIssue, type WordTiming } from './models';
import { idFor } from './wordIdentity';

export type IntervalValidation = {
  words: WordTiming[];
  issues: IntervalIssue[];
};

export function inspectIntervals(words: WordTiming[], audioDurationMs: number): IntervalValidation {
  if (words.length === 0) {
    return { words, issues: [] };
  }
  const duration = Math.max(0, audioDurationMs);
  const issues: IntervalIssue[] = [];
  let previousEnd = 0;
  let previousTimedIndex = -1;
  const validated = words.map((word, index) => {
    const id = word.id.length > 0 ? word.id : idFor(index, word.startChar, word.endChar);
    const protectedWord = isProtectedFromResync(word);
    const wordIndex = word.index >= 0 ? word.index : index;
    if (!isTimed(word)) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'MISSING_TIME',
        message: 'Palavra sem intervalo temporal.',
      });
      if (protectedWord) {
        return { ...word, id, index: wordIndex };
      }
      return {
        ...word,
        id,
        index: wordIndex,
        needsReview: true,
        state: 'REVIEW' as const,
        timestampOrigin: word.timestampOrigin ?? 'NONE',
      };
    }
    let start = Math.max(0, word.startMs as number);
    let end = Math.max(start, word.endMs as number);
    let outside = false;
    if (duration > 0) {
      if ((word.startMs as number) > duration || (word.endMs as number) > duration) {
        outside = true;
      }
      start = Math.min(start, duration);
      end = Math.min(end, duration);
    }
    if (end < start) {
      const swapped = start;
      start = end;
      end = swapped;
    }
    let repairedOverlap = false;
    if (!protectedWord && previousTimedIndex >= 0 && start < previousEnd) {
      start = previousEnd;
      repairedOverlap = true;
      if (end < start + 20) {
        const limit = duration > 0 ? duration : start + 20;
        end = Math.min(start + 20, limit);
      }
    }
    if (!protectedWord && duration > 0 && start >= duration) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'OUTSIDE_AUDIO',
        message: 'Intervalo fora da duração do áudio.',
      });
      return {
        ...word,
        id,
        index: wordIndex,
        startMs: null,
        endMs: null,
        needsReview: true,
        state: 'REVIEW' as const,
        timestampOrigin: 'NONE' as const,
      };
    }
    const overlap = previousTimedIndex >= 0 && start < previousEnd;
    const invalidOrder = previousTimedIndex >= 0 && start + 8 < previousEnd;
    const tooShort = end - start < 20 && word.text.length > 2;
    const zeroLength = end === start;
    if (outside) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'OUTSIDE_AUDIO',
        message: 'Intervalo fora da duração do áudio.',
      });
    }
    if (overlap) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'OVERLAP',
        message: 'Sobreposição com a palavra anterior.',
      });
    }
    if (invalidOrder) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'INVALID_ORDER',
        message: 'Início anterior ao fim da palavra precedente.',
      });
    }
    if (tooShort) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'TOO_SHORT',
        message: 'Intervalo demasiado curto para a palavra.',
      });
    }
    if (zeroLength) {
      issues.push({
        wordIndex: index,
        wordId: id,
        type: 'ZERO_LENGTH',
        message: 'Início e fim coincidem.',
      });
    }
    previousEnd = Math.max(previousEnd, end);
    previousTimedIndex = index;
    const invalid = invalidOrder || tooShort || zeroLength || overlap || outside || repairedOverlap;
    if (protectedWord) {
      return {
        ...word,
        id,
        index: wordIndex,
        startMs: start,
        endMs: end,
        needsReview: false,
        manuallyAdjusted: true,
      };
    }
    const review = word.needsReview || word.state === 'REVIEW' || invalid;
    return {
      ...word,
      id,
      index: wordIndex,
      startMs: start,
      endMs: end,
      needsReview: review,
      state: review ? 'REVIEW' : 'AUTO',
    };
  });
  return { words: validated, issues };
}

export function currentWordIndex(words: WordTiming[], positionMs: number): number {
  if (words.length === 0) {
    return -1;
  }
  let lastTimed = -1;
  for (let index = 0; index < words.length; index++) {
    const word = words[index];
    if (word.startMs != null && word.endMs != null) {
      lastTimed = index;
      if (positionMs >= word.startMs && positionMs < word.endMs) {
        return index;
      }
      if (positionMs < word.startMs) {
        return index === 0 ? 0 : Math.max(lastTimed, 0);
      }
    }
  }
  return lastTimed;
}
