import { NativeModules, PermissionsAndroid, Platform } from 'react-native';
import type { FileBackend } from '../storage/fileBackend';
import type { RecognizedWord, Transcription } from '../core/models';

type LsbFilesNative = {
  writeText: (path: string, text: string) => Promise<void>;
  readText: (path: string) => Promise<string | null>;
  writeBase64: (path: string, data: string) => Promise<void>;
  readBase64: (path: string) => Promise<string | null>;
  exists: (path: string) => Promise<boolean>;
  deletePath: (path: string) => Promise<void>;
  list: (path: string) => Promise<string[]>;
  pickFile: (mime: string) => Promise<string | null>;
  unzipWhitelisted: (base64: string) => Promise<Record<string, string>>;
  zipFiles: (files: Record<string, string>) => Promise<string>;
  absolutePath: (path: string) => Promise<string>;
};

type LsbAudioNative = {
  startRecording: (path: string) => Promise<void>;
  stopRecording: () => Promise<{ durationMs: number; path: string }>;
  play: (path: string) => Promise<void>;
  pause: () => Promise<void>;
  stop: () => Promise<void>;
  seek: (positionMs: number) => Promise<void>;
  status: () => Promise<{ positionMs: number; durationMs: number; playing: boolean }>;
  getLevel: () => Promise<{ recording: boolean; rms: number; peak: number; elapsedMs: number }>;
};

type LsbSpeechNative = {
  isAvailable: () => Promise<boolean>;
  transcribe: (
    path: string,
    prompt: string,
  ) => Promise<{
    words: RecognizedWord[];
    language: string;
    usedTokenTimestamps: boolean;
  }>;
};

const filesNative = NativeModules.LsbFiles as LsbFilesNative | undefined;
const audioNative = NativeModules.LsbAudio as LsbAudioNative | undefined;
const speechNative = NativeModules.LsbSpeech as LsbSpeechNative | undefined;

function requireFiles(): LsbFilesNative {
  if (!filesNative) {
    throw new Error('O módulo nativo de ficheiros não está disponível neste APK.');
  }
  return filesNative;
}

function requireAudio(): LsbAudioNative {
  if (!audioNative) {
    throw new Error('O módulo nativo de áudio não está disponível neste APK.');
  }
  return audioNative;
}

export function createNativeFileBackend(): FileBackend {
  const native = requireFiles();
  return {
    writeText: (path, text) => native.writeText(path, text),
    readText: path => native.readText(path),
    writeBase64: (path, data) => native.writeBase64(path, data),
    readBase64: path => native.readBase64(path),
    exists: path => native.exists(path),
    deletePath: path => native.deletePath(path),
    list: path => native.list(path),
  };
}

export async function pickFile(mime: string): Promise<string | null> {
  return requireFiles().pickFile(mime);
}

export async function unzipWhitelisted(base64: string): Promise<Record<string, string>> {
  return requireFiles().unzipWhitelisted(base64);
}

export async function zipFiles(files: Record<string, string>): Promise<string> {
  return requireFiles().zipFiles(files);
}

export async function absolutePath(path: string): Promise<string> {
  return requireFiles().absolutePath(path);
}

export async function requestMicrophone(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return false;
  }
  const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO, {
    title: 'Microfone',
    message: 'O Modo Professor precisa do microfone para gravar a narração neste tablet.',
    buttonPositive: 'Permitir',
    buttonNegative: 'Não permitir',
  });
  return result === PermissionsAndroid.RESULTS.GRANTED;
}

export const audioBridge = {
  startRecording: (path: string) => requireAudio().startRecording(path),
  stopRecording: () => requireAudio().stopRecording(),
  play: (path: string) => requireAudio().play(path),
  pause: () => requireAudio().pause(),
  stop: () => requireAudio().stop(),
  seek: (positionMs: number) => requireAudio().seek(positionMs),
  status: () => requireAudio().status(),
  getLevel: () => requireAudio().getLevel(),
};

export async function transcribeLocal(path: string, prompt = ''): Promise<Transcription> {
  if (!speechNative) {
    throw new Error('O módulo nativo de voz não está disponível neste APK.');
  }
  const available = await speechNative.isAvailable();
  if (!available) {
    throw new Error(
      'A biblioteca nativa whisper.cpp não foi carregada. Compile a aplicação com NDK.',
    );
  }
  const result = await speechNative.transcribe(path, prompt);
  return {
    words: result.words,
    language: result.language || 'pt',
    usedTokenTimestamps: result.usedTokenTimestamps,
  };
}

export const nativeModulesPresent = Boolean(filesNative && audioNative && speechNative);
