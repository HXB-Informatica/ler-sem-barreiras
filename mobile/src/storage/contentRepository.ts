import { LsbException } from '../core/lsbErrors';
import {
  AUDIO_PATH,
  bundleToContent,
  identicalContent,
  parseLsbFiles,
  type LsbFileMap,
} from '../core/lsb';
import type { ReadingContent } from '../core/models';
import { decodeContent, encodeContent, fromPackage } from '../core/packageFormat';
import { bytesFromBase64, bytesToBase64 } from '../core/sha256';
import { randomUuid } from '../core/uuid';
import type { FileBackend } from './fileBackend';

const INDEX_PATH = 'index.json';

function contentDir(id: string): string {
  return `contents/${id}`;
}

function contentPath(id: string): string {
  return `${contentDir(id)}/content.json`;
}

function audioPath(id: string): string {
  return `${contentDir(id)}/audio.wav`;
}

export class ContentRepository {
  constructor(private readonly files: FileBackend) {}

  async list(): Promise<ReadingContent[]> {
    const ids = await this.readIndex();
    const items: ReadingContent[] = [];
    for (const id of ids) {
      const item = await this.get(id);
      if (item) {
        items.push(item);
      }
    }
    return items.sort((a, b) => b.updatedAtEpochMs - a.updatedAtEpochMs);
  }

  async listReadyForStudent(): Promise<ReadingContent[]> {
    const all = await this.list();
    return all.filter(
      item =>
        item.audioRelativePath != null ||
        item.syncStatus === 'COMPLETED' ||
        item.syncStatus === 'COMPLETED_WITH_GAPS',
    );
  }

  async get(id: string): Promise<ReadingContent | null> {
    const raw = await this.files.readText(contentPath(id));
    if (!raw) {
      return null;
    }
    const pkg = decodeContent(raw);
    const hasAudio = await this.files.exists(audioPath(id));
    return fromPackage(pkg, hasAudio ? audioPath(id) : pkg.audioFile);
  }

  async save(content: ReadingContent, audioBase64?: string | null): Promise<ReadingContent> {
    const now = Date.now();
    const stored: ReadingContent = {
      ...content,
      updatedAtEpochMs: now,
      createdAtEpochMs: content.createdAtEpochMs || now,
      audioRelativePath: audioBase64 != null || (await this.files.exists(audioPath(content.id)))
        ? audioPath(content.id)
        : content.audioRelativePath,
    };
    await this.files.writeText(contentPath(content.id), encodeContent(stored));
    if (audioBase64 != null) {
      await this.files.writeBase64(audioPath(content.id), audioBase64);
    }
    const ids = await this.readIndex();
    if (!ids.includes(content.id)) {
      ids.push(content.id);
      await this.writeIndex(ids);
    }
    return stored;
  }

  async delete(id: string): Promise<void> {
    await this.files.deletePath(contentDir(id));
    const ids = (await this.readIndex()).filter(item => item !== id);
    await this.writeIndex(ids);
  }

  async readAudioBase64(id: string): Promise<string | null> {
    return this.files.readBase64(audioPath(id));
  }

  async importLsbFiles(files: LsbFileMap): Promise<{ content: ReadingContent; duplicate: boolean; idRemapped: boolean }> {
    const bundle = parseLsbFiles(files);
    const existing = await this.get(bundle.manifest.contentId);
    if (existing && identicalContent(existing, bundle)) {
      return { content: existing, duplicate: true, idRemapped: false };
    }
    const assignedId = existing ? randomUuid() : bundle.manifest.contentId;
    const audioRelativePath = audioPath(assignedId);
    const content = bundleToContent(bundle, assignedId, audioRelativePath);
    await this.save(content, bytesToBase64(bundle.audio));
    return { content, duplicate: false, idRemapped: assignedId !== bundle.manifest.contentId };
  }

  async importLsbBase64Map(encoded: Record<string, string>): Promise<{
    content: ReadingContent;
    duplicate: boolean;
    idRemapped: boolean;
  }> {
    const files: LsbFileMap = {};
    for (const [path, value] of Object.entries(encoded)) {
      files[path] = bytesFromBase64(value);
    }
    return this.importLsbFiles(files);
  }

  async exportLsbHint(): Promise<string> {
    return AUDIO_PATH;
  }

  private async readIndex(): Promise<string[]> {
    const raw = await this.files.readText(INDEX_PATH);
    if (!raw) {
      return [];
    }
    try {
      const parsed = JSON.parse(raw) as string[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      throw new LsbException('O índice de conteúdos está danificado.');
    }
  }

  private async writeIndex(ids: string[]): Promise<void> {
    await this.files.writeText(INDEX_PATH, `${JSON.stringify(ids, null, 2)}\n`);
  }
}
