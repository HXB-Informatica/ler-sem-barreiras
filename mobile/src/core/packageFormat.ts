import {
  CONTENT_FORMAT_ID,
  CONTENT_FORMAT_VERSION,
  type ContentPackage,
  type ReadingContent,
} from './models';
import { ensure } from './wordIdentity';

export function toPackage(content: ReadingContent): ContentPackage {
  return {
    format: CONTENT_FORMAT_ID,
    version: CONTENT_FORMAT_VERSION,
    id: content.id,
    title: content.title,
    text: content.text,
    audioFile: content.audioRelativePath ? content.audioRelativePath.split('/').pop() ?? null : null,
    durationMs: content.durationMs,
    words: ensure(content.text, content.words),
    syncStatus: content.syncStatus,
    createdAtEpochMs: content.createdAtEpochMs,
    updatedAtEpochMs: content.updatedAtEpochMs,
  };
}

export function fromPackage(pkg: ContentPackage, audioRelativePath?: string | null): ReadingContent {
  return {
    id: pkg.id,
    title: pkg.title,
    text: pkg.text,
    audioRelativePath: audioRelativePath ?? pkg.audioFile,
    durationMs: pkg.durationMs,
    words: ensure(pkg.text, pkg.words),
    syncStatus: pkg.syncStatus,
    createdAtEpochMs: pkg.createdAtEpochMs,
    updatedAtEpochMs: pkg.updatedAtEpochMs,
  };
}

export function encodeContent(content: ReadingContent): string {
  return `${JSON.stringify(toPackage(content), null, 2)}\n`;
}

export function decodeContent(raw: string): ContentPackage {
  const pkg = JSON.parse(raw) as ContentPackage;
  return { ...pkg, words: ensure(pkg.text, pkg.words ?? []) };
}
