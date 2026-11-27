import { alignmentStatus, type ReadingContent, type SyncStatus, type WordTiming } from './models';
import { inspectIntervals } from './timeValidator';
import { tokenize } from './tokenizer';
import { sha256Bytes, sha256Utf8 } from './sha256';
import { wavDurationMs } from './wavProbe';
import { ensure } from './wordIdentity';
import { LsbException } from './lsbErrors';

export const LSB_FORMAT_ID = 'lersembarreiras-lsb';
export const LSB_SCHEMA_VERSION = 1;
export const LSB_MAX_SCHEMA = 1;
export const MANIFEST_PATH = 'manifest.json';
export const METADATA_PATH = 'metadata.json';
export const TEXT_PATH = 'texto.txt';
export const WORDS_PATH = 'sync/palavras.json';
export const AUDIO_PATH = 'audio/leitura.wav';
export const ALLOWED_LSB_FILES = [MANIFEST_PATH, METADATA_PATH, TEXT_PATH, WORDS_PATH, AUDIO_PATH] as const;

export type LsbManifest = {
  format: string;
  schemaVersion: number;
  contentId: string;
  files: Record<string, string>;
  createdAtEpochMs: number;
};

export type LsbMetadata = {
  title: string;
  durationMs: number;
  syncStatus: SyncStatus;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  originalContentId: string;
  source: string;
  audioFile: string;
  textFile: string;
  wordsFile: string;
};

export type LsbBundle = {
  manifest: LsbManifest;
  metadata: LsbMetadata;
  text: string;
  words: WordTiming[];
  audio: Uint8Array;
};

export type LsbFileMap = Record<string, Uint8Array>;

function decodeUtf8(bytes: Uint8Array): string {
  return new TextDecoder('utf-8').decode(bytes);
}

function encodeUtf8(text: string): Uint8Array {
  return new TextEncoder().encode(text);
}

function prettyJson(value: unknown): string {
  return `${JSON.stringify(value, null, 2)}\n`;
}

export function validateWords(text: string, words: WordTiming[], durationMs: number): void {
  const tokens = tokenize(text);
  if (tokens.length !== words.length) {
    throw new LsbException('O número de palavras não corresponde ao texto original.');
  }
  tokens.forEach((token, index) => {
    if (token.original !== words[index].text) {
      throw new LsbException(`A ordem das palavras não coincide com o texto (posição ${index + 1}).`);
    }
  });
  inspectIntervals(words, durationMs);
}

export function parseLsbFiles(files: LsbFileMap): LsbBundle {
  const manifestBytes = files[MANIFEST_PATH];
  if (!manifestBytes) {
    throw new LsbException('Falta manifest.json. Este ficheiro não é um pacote Ler sem Barreiras.');
  }
  let manifest: LsbManifest;
  try {
    manifest = JSON.parse(decodeUtf8(manifestBytes)) as LsbManifest;
  } catch {
    throw new LsbException('O manifesto do pacote está danificado.');
  }
  if (manifest.format !== LSB_FORMAT_ID) {
    throw new LsbException('Este ficheiro não é um pacote Ler sem Barreiras (.lsb).');
  }
  if (manifest.schemaVersion > LSB_MAX_SCHEMA) {
    throw new LsbException(
      `Este pacote usa a versão ${manifest.schemaVersion}, mais recente do que esta aplicação. Actualize o Ler sem Barreiras.`,
    );
  }
  if (manifest.schemaVersion < 1) {
    throw new LsbException('A versão do pacote não é reconhecida.');
  }

  const requireFile = (path: string): Uint8Array => {
    const bytes = files[path];
    if (!bytes) {
      throw new LsbException(`Falta o ficheiro obrigatório ${path}.`);
    }
    const expected = manifest.files[path];
    if (!expected) {
      throw new LsbException(`O manifesto não indica a soma de verificação de ${path}.`);
    }
    const actual = sha256Bytes(bytes);
    if (expected.toLowerCase() !== actual.toLowerCase()) {
      throw new LsbException(`A soma de verificação de ${path} não coincide. O pacote pode ter sido alterado.`);
    }
    return bytes;
  };

  const metadata = JSON.parse(decodeUtf8(requireFile(METADATA_PATH))) as LsbMetadata;
  const text = decodeUtf8(requireFile(TEXT_PATH)).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const words = ensure(text, JSON.parse(decodeUtf8(requireFile(WORDS_PATH))) as WordTiming[]);
  const audio = requireFile(AUDIO_PATH);
  if (text.trim().length === 0) {
    throw new LsbException('O texto do exercício está vazio.');
  }
  validateWords(text, words, metadata.durationMs);
  const audioDuration = wavDurationMs(audio);
  if (metadata.durationMs > 0 && Math.abs(audioDuration - metadata.durationMs) > 250) {
    throw new LsbException('A duração do áudio não corresponde aos metadados do pacote.');
  }
  if ((metadata.audioFile ?? '').includes('..') || (metadata.textFile ?? '').includes('..')) {
    throw new LsbException('O pacote contém referências internas inválidas.');
  }
  return { manifest, metadata, text, words, audio };
}

export function buildLsbFiles(
  content: ReadingContent,
  audio: Uint8Array,
  source = 'android',
): LsbFileMap {
  if (content.text.trim().length === 0) {
    throw new LsbException('O exercício não tem texto para exportar.');
  }
  if (audio.length < 44) {
    throw new LsbException('O exercício não tem uma narração WAV para exportar.');
  }
  const text = content.text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const words = ensure(text, content.words);
  validateWords(text, words, content.durationMs);
  const audioDuration = wavDurationMs(audio);
  if (content.durationMs > 0 && Math.abs(audioDuration - content.durationMs) > 250) {
    throw new LsbException('A duração declarada não corresponde ao ficheiro de áudio.');
  }
  const metadata: LsbMetadata = {
    title: content.title.trim().length > 0 ? content.title : 'Sem título',
    durationMs: content.durationMs > 0 ? content.durationMs : audioDuration,
    syncStatus: content.syncStatus,
    createdAtEpochMs: content.createdAtEpochMs,
    updatedAtEpochMs: content.updatedAtEpochMs,
    originalContentId: content.id,
    source,
    audioFile: AUDIO_PATH,
    textFile: TEXT_PATH,
    wordsFile: WORDS_PATH,
  };
  const textBytes = encodeUtf8(text);
  const wordsBytes = encodeUtf8(prettyJson(words));
  const metaBytes = encodeUtf8(prettyJson(metadata));
  const files: Record<string, string> = {
    [METADATA_PATH]: sha256Bytes(metaBytes),
    [TEXT_PATH]: sha256Bytes(textBytes),
    [WORDS_PATH]: sha256Bytes(wordsBytes),
    [AUDIO_PATH]: sha256Bytes(audio),
  };
  const manifest: LsbManifest = {
    format: LSB_FORMAT_ID,
    schemaVersion: LSB_SCHEMA_VERSION,
    contentId: content.id,
    files,
    createdAtEpochMs: content.createdAtEpochMs,
  };
  return {
    [MANIFEST_PATH]: encodeUtf8(prettyJson(manifest)),
    [METADATA_PATH]: metaBytes,
    [TEXT_PATH]: textBytes,
    [WORDS_PATH]: wordsBytes,
    [AUDIO_PATH]: audio,
  };
}

export function bundleToContent(bundle: LsbBundle, assignedId: string, audioRelativePath: string): ReadingContent {
  return {
    id: assignedId,
    title: bundle.metadata.title,
    text: bundle.text,
    audioRelativePath,
    durationMs: bundle.metadata.durationMs,
    words: bundle.words,
    syncStatus: bundle.metadata.syncStatus,
    createdAtEpochMs: bundle.metadata.createdAtEpochMs,
    updatedAtEpochMs: bundle.metadata.updatedAtEpochMs,
    availableToStudents:
      bundle.metadata.syncStatus === 'COMPLETED' || bundle.metadata.syncStatus === 'COMPLETED_WITH_GAPS',
  };
}

export function identicalContent(existing: ReadingContent, bundle: LsbBundle): boolean {
  if (existing.text !== bundle.text || existing.words.length !== bundle.words.length) {
    return false;
  }
  return existing.words.every((left, index) => {
    const right = bundle.words[index];
    return left.text === right.text && left.startMs === right.startMs && left.endMs === right.endMs;
  });
}

export function syncLabel(status: SyncStatus): string {
  switch (status) {
    case 'NONE':
      return 'Ainda não sincronizado';
    case 'IN_PROGRESS':
      return 'Sincronização em curso';
    case 'COMPLETED':
      return 'Pronto para os alunos';
    case 'COMPLETED_WITH_GAPS':
      return 'Sincronizado — convém rever algumas palavras';
    case 'FAILED':
      return 'Sincronização falhou';
    case 'CANCELLED':
      return 'Sincronização cancelada';
    default:
      return status;
  }
}

export function statusFromAlignment(alignedCount: number, reviewCount: number, wordCount: number): SyncStatus {
  return alignmentStatus({
    words: Array.from({ length: wordCount }, () => ({}) as WordTiming),
    alignedCount,
    reviewCount,
    unusedRecognizedCount: 0,
    intervalIssues: [],
  });
}

export { sha256Utf8 };
