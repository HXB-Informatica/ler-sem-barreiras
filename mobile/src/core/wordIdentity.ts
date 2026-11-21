import {
  emptyWordTiming,
  resolvedOrigin,
  resolvedState,
  type TextToken,
  type TimestampOrigin,
  type WordState,
  type WordTiming,
} from './models';
import { normalizeForMatch, tokenize } from './tokenizer';

export function idFor(index: number, startChar: number, endChar: number): string {
  return `w-${index}-${startChar}-${endChar}`;
}

export function fromToken(args: {
  token: TextToken;
  index: number;
  startMs: number | null;
  endMs: number | null;
  confidence: number;
  state: WordState;
  origin: TimestampOrigin;
  needsReview: boolean;
  existingId?: string;
}): WordTiming {
  const review = args.needsReview || args.startMs == null || args.endMs == null;
  const resolved = args.state === 'AUTO' && review ? 'REVIEW' : args.state;
  return emptyWordTiming({
    text: args.token.original,
    startChar: args.token.startChar,
    endChar: args.token.endChar,
    startMs: args.startMs,
    endMs: args.endMs,
    confidence: args.confidence,
    manuallyAdjusted: resolved === 'MANUAL' || resolved === 'VERIFIED',
    needsReview: resolved === 'REVIEW',
    id: args.existingId && args.existingId.length > 0 ? args.existingId : idFor(args.index, args.token.startChar, args.token.endChar),
    index: args.index,
    state: resolved,
    timestampOrigin: args.origin,
  });
}

export function unmatched(token: TextToken, index: number, existingId = ''): WordTiming {
  return fromToken({
    token,
    index,
    startMs: null,
    endMs: null,
    confidence: 0,
    state: 'REVIEW',
    origin: 'NONE',
    needsReview: true,
    existingId,
  });
}

export function matches(word: WordTiming, token: TextToken): boolean {
  if (word.text === token.original) {
    return true;
  }
  return normalizeForMatch(word.text) === token.normalized;
}

export function populate(word: WordTiming, token: TextToken, index: number): WordTiming {
  return fromToken({
    token,
    index,
    startMs: word.startMs,
    endMs: word.endMs,
    confidence: word.confidence,
    state: resolvedState(word),
    origin: resolvedOrigin(word),
    needsReview: word.needsReview || resolvedState(word) === 'REVIEW',
    existingId: word.id,
  });
}

export function ensure(sourceText: string, words: WordTiming[]): WordTiming[] {
  const tokens = tokenize(sourceText);
  if (tokens.length === 0) {
    return [];
  }
  if (words.length === tokens.length) {
    return tokens.map((token, index) => populate(words[index], token, index));
  }
  return tokens.map((token, index) => {
    const previous =
      words[index] && matches(words[index], token)
        ? words[index]
        : words.find(word => word.index === index && matches(word, token));
    return previous ? populate(previous, token, index) : unmatched(token, index);
  });
}
