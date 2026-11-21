import { levenshtein, normalizeForMatch, tokenize } from '../tokenizer';

describe('WordTokenizer', () => {
  it('splits Portuguese sentence and keeps original spelling', () => {
    const text = 'Olá, mundo!';
    const tokens = tokenize(text);
    expect(tokens).toHaveLength(2);
    expect(tokens[0].original).toBe('Olá,');
    expect(tokens[0].normalized).toBe('ola');
    expect(tokens[0].startChar).toBe(0);
    expect(tokens[1].original).toBe('mundo!');
    expect(tokens[1].normalized).toBe('mundo');
  });

  it('handles repeated words and punctuation', () => {
    const tokens = tokenize('O gato, o gato corre.');
    expect(tokens.map(token => token.normalized)).toEqual(['o', 'gato', 'o', 'gato', 'corre']);
    expect(tokens[1].original).toBe('gato,');
  });

  it('ignores punctuation-only tokens', () => {
    const tokens = tokenize('Um — dois.');
    expect(tokens.map(token => token.normalized)).toEqual(['um', 'dois']);
  });

  it('preserves character offsets', () => {
    const text = 'Boa tarde';
    const tokens = tokenize(text);
    expect(text.slice(tokens[0].startChar, tokens[0].endChar)).toBe('Boa');
    expect(text.slice(tokens[1].startChar, tokens[1].endChar)).toBe('tarde');
  });

  it('maps digits to Portuguese number words', () => {
    expect(normalizeForMatch('1')).toBe('um');
    expect(normalizeForMatch('2')).toBe('dois');
    expect(normalizeForMatch('3')).toBe('tres');
    expect(normalizeForMatch('um')).toBe('um');
  });

  it('computes levenshtein for small ASR errors', () => {
    expect(levenshtein('casa', 'casa')).toBe(0);
    expect(levenshtein('casa', 'casas')).toBe(1);
    expect(levenshtein('rapido', 'rapido')).toBeLessThan(levenshtein('rapido', 'lento'));
  });
});
