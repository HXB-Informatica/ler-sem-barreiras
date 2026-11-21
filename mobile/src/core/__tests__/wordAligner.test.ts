import { alignmentStatus } from '../models';
import { similarity, alignText } from '../wordAligner';
import type { RecognizedWord } from '../models';

function rec(text: string, startMs: number, endMs: number, probability = 0.9): RecognizedWord {
  return { text, startMs, endMs, probability };
}

describe('WordAligner', () => {
  it('matches words with timestamps ignoring case and punctuation', () => {
    const result = alignText(
      'Olá, mundo!',
      [rec('ola', 100, 400, 0.9), rec('mundo', 420, 800, 0.8)],
      1000,
    );
    expect(result.alignedCount).toBe(2);
    expect(result.words[0].text).toBe('Olá,');
    expect(result.words[0].startMs).toBe(100);
    expect(result.words[1].text).toBe('mundo!');
    expect(alignmentStatus(result)).toBe('COMPLETED');
  });

  it('keeps repeated words in order', () => {
    const result = alignText(
      'o gato o gato',
      [rec('o', 0, 80), rec('gato', 90, 300), rec('o', 400, 470), rec('gato', 480, 700)],
      800,
    );
    expect(result.words.map(word => word.startMs)).toEqual([0, 90, 400, 480]);
  });

  it('interpolates short internal gaps as review not ASR', () => {
    const result = alignText(
      'o gato preto corre',
      [rec('o', 0, 80), rec('gato', 90, 300), rec('corre', 700, 980)],
      1000,
    );
    const omitted = result.words.find(word => word.text === 'preto');
    expect(omitted?.startMs).toBe(300);
    expect(omitted?.endMs).toBe(700);
    expect(omitted?.needsReview).toBe(true);
    expect(omitted?.timestampOrigin).toBe('ALIGNED');
    expect(result.alignedCount).toBe(4);
    expect(alignmentStatus(result)).toBe('COMPLETED_WITH_GAPS');
  });

  it('does not invent times across long pauses', () => {
    const result = alignText(
      'o gato preto corre',
      [rec('o', 0, 80), rec('gato', 90, 300), rec('corre', 2200, 2500)],
      2600,
    );
    const omitted = result.words.find(word => word.text === 'preto');
    expect(omitted?.startMs).toBeNull();
    expect(omitted?.endMs).toBeNull();
    expect(omitted?.needsReview).toBe(true);
  });

  it('ignores hesitations and extra recognized words', () => {
    const result = alignText('bom dia', [rec('uh', 0, 120, 0.2), rec('bom', 150, 320), rec('dia', 340, 520)], 600);
    expect(result.words[0].startMs).toBe(150);
    expect(result.words[1].startMs).toBe(340);
    expect(result.alignedCount).toBe(2);
  });

  it('aligns despite ASR spelling errors', () => {
    const result = alignText('rapido', [rec('rapidoo', 10, 400, 0.6)], 500);
    expect(result.words[0].startMs).toBe(10);
    expect(result.words[0].needsReview).toBe(true);
  });

  it('handles long pauses between words', () => {
    const result = alignText('um dois', [rec('um', 0, 200), rec('dois', 2400, 2700)], 3000);
    expect(result.words[0].startMs).toBe(0);
    expect(result.words[1].startMs).toBe(2400);
    expect(result.words.every(word => word.startMs != null)).toBe(true);
  });

  it('marks every word for review when recognition is empty', () => {
    const result = alignText('três palavras aqui', [], 1000);
    expect(result.alignedCount).toBe(0);
    expect(result.words.every(word => word.needsReview && word.startMs == null)).toBe(true);
    expect(alignmentStatus(result)).toBe('FAILED');
  });

  it('treats accent-insensitive tokens as equal after normalization', () => {
    expect(similarity('acao', 'acao')).toBeGreaterThan(0);
    expect(similarity('casa', 'casas')).toBeGreaterThan(0);
    expect(similarity('mesa', 'gato')).toBeLessThan(0);
  });

  it('matches spoken digits to number words', () => {
    const result = alignText('um dois três', [rec('1', 0, 120), rec('2', 140, 280), rec('3', 300, 440)], 500);
    expect(result.alignedCount).toBe(3);
    expect(result.words[0].startMs).toBe(0);
    expect(result.words[1].startMs).toBe(140);
  });

  it('merges split tokens and ignores fillers', () => {
    const result = alignText(
      'vamos ler',
      [rec('uh', 0, 80, 0.2), rec('va', 100, 220, 0.8), rec('mos', 220, 360, 0.8), rec('ler', 400, 560, 0.9)],
      600,
    );
    expect(result.words[0].startMs).toBe(100);
    expect(result.words[1].startMs).toBe(400);
    expect(result.alignedCount).toBe(2);
  });
});
