export type SyncStatus =
  | 'NONE'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'COMPLETED_WITH_GAPS'
  | 'FAILED'
  | 'CANCELLED';

export type PresentationMode = 'FULL_TEXT' | 'SINGLE_WORD' | 'PROGRESSIVE';

export type WordState = 'AUTO' | 'REVIEW' | 'MANUAL' | 'VERIFIED';

export type TimestampOrigin = 'NONE' | 'ASR' | 'ALIGNED' | 'MANUAL' | 'IMPORTED';

export type WordTiming = {
  text: string;
  startChar: number;
  endChar: number;
  startMs: number | null;
  endMs: number | null;
  confidence: number;
  manuallyAdjusted: boolean;
  needsReview: boolean;
  id: string;
  index: number;
  state: WordState | null;
  timestampOrigin: TimestampOrigin | null;
};

export type ReadingContent = {
  id: string;
  title: string;
  text: string;
  audioRelativePath: string | null;
  durationMs: number;
  words: WordTiming[];
  syncStatus: SyncStatus;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  /** When omitted, legacy contents stay visible if they already look ready. */
  availableToStudents?: boolean;
};

export function isReadyForStudent(item: ReadingContent): boolean {
  if (item.availableToStudents === false) {
    return false;
  }
  if (!item.audioRelativePath || !item.text.trim()) {
    return false;
  }
  if (item.syncStatus !== 'COMPLETED') {
    return false;
  }
  return reviewCount(item) === 0;
}

export type ContentPackage = {
  format: string;
  version: number;
  id: string;
  title: string;
  text: string;
  audioFile: string | null;
  durationMs: number;
  words: WordTiming[];
  syncStatus: SyncStatus;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  availableToStudents?: boolean;
};

export const CONTENT_FORMAT_ID = 'lersembarreiras-content';
export const CONTENT_FORMAT_VERSION = 1;

export type TextToken = {
  original: string;
  normalized: string;
  startChar: number;
  endChar: number;
};

export type RecognizedWord = {
  text: string;
  startMs: number;
  endMs: number;
  probability: number;
};

export type Transcription = {
  words: RecognizedWord[];
  language: string;
  usedTokenTimestamps: boolean;
};

export type IntervalIssueType =
  | 'MISSING_TIME'
  | 'INVALID_ORDER'
  | 'OVERLAP'
  | 'TOO_SHORT'
  | 'ZERO_LENGTH'
  | 'OUTSIDE_AUDIO';

export type IntervalIssue = {
  wordIndex: number;
  wordId: string;
  type: IntervalIssueType;
  message: string;
};

export type AlignmentResult = {
  words: WordTiming[];
  alignedCount: number;
  reviewCount: number;
  unusedRecognizedCount: number;
  intervalIssues: IntervalIssue[];
};

export type ReaderSettings = {
  presentationMode: PresentationMode;
  fontScale: number;
  highContrast: boolean;
};

export function isTimed(word: WordTiming): boolean {
  return word.startMs != null && word.endMs != null && word.endMs >= word.startMs;
}

export function resolvedState(word: WordTiming): WordState {
  if (word.state) {
    return word.state;
  }
  if (word.manuallyAdjusted) {
    return 'MANUAL';
  }
  if (word.needsReview || !isTimed(word)) {
    return 'REVIEW';
  }
  return 'AUTO';
}

export function resolvedOrigin(word: WordTiming): TimestampOrigin {
  if (word.timestampOrigin) {
    return word.timestampOrigin;
  }
  if (word.manuallyAdjusted) {
    return 'MANUAL';
  }
  if (isTimed(word)) {
    return 'ASR';
  }
  return 'NONE';
}

export function isProtectedFromResync(word: WordTiming): boolean {
  const current = resolvedState(word);
  return current === 'MANUAL' || current === 'VERIFIED';
}

export function requiresReview(word: WordTiming): boolean {
  const current = resolvedState(word);
  if (current === 'MANUAL' || current === 'VERIFIED') {
    return false;
  }
  return current === 'REVIEW' || !isTimed(word);
}

export function alignmentStatus(result: AlignmentResult): SyncStatus {
  if (result.words.length === 0 || result.alignedCount === 0) {
    return 'FAILED';
  }
  if (result.reviewCount > 0) {
    return 'COMPLETED_WITH_GAPS';
  }
  return 'COMPLETED';
}

export function reviewCount(content: ReadingContent): number {
  return content.words.filter(requiresReview).length;
}

export function emptyWordTiming(partial: Partial<WordTiming> & Pick<WordTiming, 'text' | 'startChar' | 'endChar'>): WordTiming {
  return {
    startMs: null,
    endMs: null,
    confidence: 0,
    manuallyAdjusted: false,
    needsReview: true,
    id: '',
    index: -1,
    state: null,
    timestampOrigin: null,
    ...partial,
  };
}
