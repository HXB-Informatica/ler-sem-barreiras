import { LsbException } from './lsbErrors';

function ascii(bytes: Uint8Array, offset: number): string {
  return String.fromCharCode(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
}

function readInt32LE(bytes: Uint8Array, offset: number): number {
  return (
    bytes[offset] |
    (bytes[offset + 1] << 8) |
    (bytes[offset + 2] << 16) |
    (bytes[offset + 3] << 24)
  );
}

function readInt16LE(bytes: Uint8Array, offset: number): number {
  const value = bytes[offset] | (bytes[offset + 1] << 8);
  return value > 32767 ? value - 65536 : value;
}

export function wavDurationMs(bytes: Uint8Array): number {
  if (bytes.length < 44) {
    throw new LsbException('O áudio do pacote está incompleto.');
  }
  if (ascii(bytes, 0) !== 'RIFF' || ascii(bytes, 8) !== 'WAVE') {
    throw new LsbException('O áudio do pacote não é um WAV PCM válido.');
  }
  let offset = 12;
  let sampleRate = 0;
  let channels = 0;
  let bits = 0;
  let dataBytes = 0;
  while (offset + 8 <= bytes.length) {
    const id = ascii(bytes, offset);
    const size = readInt32LE(bytes, offset + 4) >>> 0;
    offset += 8;
    if (id === 'fmt ') {
      if (offset + 16 > bytes.length) {
        break;
      }
      const format = readInt16LE(bytes, offset);
      if (format !== 1) {
        throw new LsbException('O áudio tem de ser PCM sem compressão.');
      }
      channels = readInt16LE(bytes, offset + 2);
      sampleRate = readInt32LE(bytes, offset + 4);
      bits = readInt16LE(bytes, offset + 14);
    } else if (id === 'data') {
      dataBytes = Math.max(0, size);
      break;
    }
    offset += size + (size % 2);
  }
  const frame = (bits / 8) * Math.max(1, channels);
  if (sampleRate <= 0 || frame <= 0) {
    throw new LsbException('O cabeçalho WAV está danificado.');
  }
  return Math.floor((dataBytes * 1000) / (sampleRate * frame));
}
