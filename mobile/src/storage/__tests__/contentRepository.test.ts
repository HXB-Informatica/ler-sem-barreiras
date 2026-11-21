import { alignText } from '../../core/wordAligner';
import { ContentRepository } from '../contentRepository';
import { createMemoryBackend } from '../fileBackend';

describe('ContentRepository', () => {
  it('saves and lists contents without a network', async () => {
    const repo = new ContentRepository(createMemoryBackend());
    await repo.save({
      id: 'a',
      title: 'Primeiro texto',
      text: 'Olá mundo',
      audioRelativePath: null,
      durationMs: 0,
      words: [],
      syncStatus: 'NONE',
      createdAtEpochMs: 10,
      updatedAtEpochMs: 10,
    });
    const list = await repo.list();
    expect(list).toHaveLength(1);
    expect(list[0].title).toBe('Primeiro texto');
    expect(await repo.listReadyForStudent()).toHaveLength(0);
  });

  it('exposes synchronized content to the student mode', async () => {
    const repo = new ContentRepository(createMemoryBackend());
    const text = 'bom dia';
    const aligned = alignText(
      text,
      [
        { text: 'bom', startMs: 0, endMs: 200, probability: 0.9 },
        { text: 'dia', startMs: 220, endMs: 400, probability: 0.9 },
      ],
      500,
    );
    await repo.save({
      id: 'b',
      title: 'Saudação',
      text,
      audioRelativePath: 'audio.wav',
      durationMs: 500,
      words: aligned.words,
      syncStatus: 'COMPLETED',
      createdAtEpochMs: 1,
      updatedAtEpochMs: 1,
    });
    const ready = await repo.listReadyForStudent();
    expect(ready).toHaveLength(1);
    expect(ready[0].words).toHaveLength(2);
  });
});
