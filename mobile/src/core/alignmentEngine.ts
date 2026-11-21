import type { AlignmentResult, Transcription, WordTiming } from './models';
import { isTimed, isProtectedFromResync, requiresReview } from './models';
import { inspectIntervals } from './timeValidator';
import { normalizeForMatch, tokenize } from './tokenizer';
import { alignTokens } from './wordAligner';
import { ensure } from './wordIdentity';

export type AlignmentRequest = {
  sourceText: string;
  transcription: Transcription;
  audioDurationMs: number;
  previousWords: WordTiming[];
};

export function align(request: AlignmentRequest): AlignmentResult {
  const tokens = tokenize(request.sourceText);
  const automatic = alignTokens(tokens, request.transcription.words, request.audioDurationMs);
  const identified = ensure(request.sourceText, automatic.words);
  const merged = mergeProtected(identified, request.previousWords);
  const validated = inspectIntervals(merged, request.audioDurationMs);
  return {
    words: validated.words,
    alignedCount: validated.words.filter(isTimed).length,
    reviewCount: validated.words.filter(requiresReview).length,
    unusedRecognizedCount: automatic.unusedRecognizedCount,
    intervalIssues: validated.issues,
  };
}

export function mergeProtected(automatic: WordTiming[], previous: WordTiming[]): WordTiming[] {
  if (previous.length === 0) {
    return automatic;
  }
  return automatic.map((auto, index) => {
    const prev =
      previous[index] ?? previous.find(word => word.id.length > 0 && word.id === auto.id);
    if (prev && isProtectedFromResync(prev) && sameLexicalWord(prev, auto)) {
      return {
        ...auto,
        id: prev.id.length > 0 ? prev.id : auto.id,
        index: auto.index,
        startMs: prev.startMs,
        endMs: prev.endMs,
        confidence: prev.confidence,
        manuallyAdjusted: true,
        needsReview: false,
        state: prev.state === 'VERIFIED' ? 'VERIFIED' : 'MANUAL',
        timestampOrigin: prev.timestampOrigin === 'NONE' || !prev.timestampOrigin ? 'MANUAL' : prev.timestampOrigin,
      };
    }
    return { ...auto, id: prev?.id && prev.id.length > 0 ? prev.id : auto.id };
  });
}

function sameLexicalWord(previous: WordTiming, automatic: WordTiming): boolean {
  if (previous.text === automatic.text) {
    return true;
  }
  return normalizeForMatch(previous.text) === normalizeForMatch(automatic.text);
}
