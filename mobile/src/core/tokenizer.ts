import type { TextToken } from './models';

const COMBINING_MARKS = /\p{M}+/gu;
const NON_ALNUM = /[^\p{L}\p{N}]+/gu;

const NUMBER_WORDS: Record<string, string> = {
  '0': 'zero',
  '1': 'um',
  '2': 'dois',
  '3': 'tres',
  '4': 'quatro',
  '5': 'cinco',
  '6': 'seis',
  '7': 'sete',
  '8': 'oito',
  '9': 'nove',
  '10': 'dez',
};

export function tokenize(text: string): TextToken[] {
  const tokens: TextToken[] = [];
  let index = 0;
  const length = text.length;
  while (index < length) {
    while (index < length && /\s/.test(text[index])) {
      index += 1;
    }
    if (index >= length) {
      break;
    }
    const start = index;
    while (index < length && !/\s/.test(text[index])) {
      index += 1;
    }
    const original = text.slice(start, index);
    const normalized = normalizeForMatch(original);
    if (normalized.length > 0) {
      tokens.push({ original, normalized, startChar: start, endChar: index });
    }
  }
  return tokens;
}

export function normalizeForMatch(word: string): string {
  const stripped = word
    .normalize('NFD')
    .replace(COMBINING_MARKS, '')
    .toLocaleLowerCase('pt-PT')
    .replace(NON_ALNUM, '');
  return NUMBER_WORDS[stripped] ?? stripped;
}

export function levenshtein(left: string, right: string): number {
  if (left === right) {
    return 0;
  }
  if (left.length === 0) {
    return right.length;
  }
  if (right.length === 0) {
    return left.length;
  }
  const previous = Array.from({ length: right.length + 1 }, (_, i) => i);
  const current = new Array<number>(right.length + 1);
  for (let i = 0; i < left.length; i++) {
    current[0] = i + 1;
    for (let j = 0; j < right.length; j++) {
      const cost = left[i] === right[j] ? 0 : 1;
      current[j + 1] = Math.min(current[j] + 1, previous[j + 1] + 1, previous[j] + cost);
    }
    for (let j = 0; j < previous.length; j++) {
      previous[j] = current[j];
    }
  }
  return previous[right.length];
}
