import { buildLsbFiles, parseLsbFiles } from '../lsb';
import type { ReadingContent } from '../models';
import { sha256Bytes } from '../sha256';
import { alignText } from '../wordAligner';

function pcmWav(durationMs: number): Uint8Array {
  const sampleRate = 16000;
  const samples = Math.floor((sampleRate * durationMs) / 1000);
  const dataBytes = samples * 2;
  const bytes = new Uint8Array(44 + dataBytes);
  const view = new DataView(bytes.buffer);
  const writeAscii = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) {
      bytes[offset + i] = text.charCodeAt(i);
    }
  };
  writeAscii(0, 'RIFF');
  view.setUint32(4, 36 + dataBytes, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(36, 'data');
  view.setUint32(40, dataBytes, true);
  return bytes;
}

describe('LSB package', () => {
  it('hashes the SHA-256 test vector', () => {
    expect(sha256Bytes(new TextEncoder().encode('abc'))).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  });

  it('round-trips a synchronized exercise', () => {
    const text = 'Olá, mundo!';
    const aligned = alignText(text, [
      { text: 'ola', startMs: 100, endMs: 400, probability: 0.9 },
      { text: 'mundo', startMs: 420, endMs: 800, probability: 0.8 },
    ], 1000);
    const content: ReadingContent = {
      id: '11111111-1111-4111-8111-111111111111',
      title: 'Saudação',
      text,
      audioRelativePath: 'audio.wav',
      durationMs: 1000,
      words: aligned.words,
      syncStatus: 'COMPLETED',
      createdAtEpochMs: 1,
      updatedAtEpochMs: 1,
    };
    const files = buildLsbFiles(content, pcmWav(1000), 'android');
    const bundle = parseLsbFiles(files);
    expect(bundle.text).toBe(text);
    expect(bundle.words).toHaveLength(2);
    expect(bundle.metadata.title).toBe('Saudação');
    expect(bundle.manifest.contentId).toBe(content.id);
  });
});
