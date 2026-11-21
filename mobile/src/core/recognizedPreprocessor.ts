import type { RecognizedWord, TextToken } from './models';
import { normalizeForMatch } from './tokenizer';

const FILLERS = new Set(['uh', 'hum', 'hmm', 'eh', 'ah', 'er', 'erm', 'euh', 'ahh']);

export function prepareRecognized(source: TextToken[], recognized: RecognizedWord[]): RecognizedWord[] {
  if (recognized.length === 0) {
    return recognized;
  }
  const wanted = new Set(source.map(token => token.normalized).filter(Boolean));
  const withoutFillers = recognized.filter(word => {
    const norm = normalizeForMatch(word.text);
    return norm.length > 0 && (!FILLERS.has(norm) || wanted.has(norm));
  });
  return mergeFragments(wanted, withoutFillers);
}

function mergeFragments(wanted: Set<string>, recognized: RecognizedWord[]): RecognizedWord[] {
  if (recognized.length < 2) {
    return recognized;
  }
  const out: RecognizedWord[] = [];
  let index = 0;
  while (index < recognized.length) {
    const current = recognized[index];
    const next = recognized[index + 1];
    const left = normalizeForMatch(current.text);
    const right = next ? normalizeForMatch(next.text) : '';
    const combined = left + right;
    if (next && wanted.has(combined) && !wanted.has(left) && combined.length >= 3) {
      out.push({
        text: current.text + next.text,
        startMs: current.startMs,
        endMs: Math.max(next.endMs, current.endMs),
        probability: Math.min(current.probability, next.probability),
      });
      index += 2;
    } else {
      out.push(current);
      index += 1;
    }
  }
  return out;
}
