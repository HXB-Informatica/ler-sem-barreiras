import { emptyWordTiming, isReadyForStudent, type ReadingContent } from '../models';

function content(partial: Partial<ReadingContent>): ReadingContent {
  return {
    id: 'id',
    title: 'Título',
    text: 'Olá mundo',
    audioRelativePath: 'contents/id/audio.wav',
    durationMs: 400,
    words: [
      emptyWordTiming({
        text: 'Olá',
        startChar: 0,
        endChar: 3,
        startMs: 0,
        endMs: 180,
        needsReview: false,
        state: 'AUTO',
      }),
      emptyWordTiming({
        text: 'mundo',
        startChar: 4,
        endChar: 9,
        startMs: 200,
        endMs: 400,
        needsReview: false,
        state: 'AUTO',
      }),
    ],
    syncStatus: 'COMPLETED',
    createdAtEpochMs: 1,
    updatedAtEpochMs: 1,
    ...partial,
  };
}

describe('isReadyForStudent', () => {
  it('keeps fully synchronized legacy content visible', () => {
    expect(isReadyForStudent(content({}))).toBe(true);
  });

  it('hides drafts even when audio exists', () => {
    expect(isReadyForStudent(content({ availableToStudents: false }))).toBe(false);
  });

  it('hides incomplete content without audio', () => {
    expect(isReadyForStudent(content({ audioRelativePath: null }))).toBe(false);
  });

  it('hides content still marked for review', () => {
    expect(
      isReadyForStudent(
        content({
          syncStatus: 'COMPLETED_WITH_GAPS',
          availableToStudents: true,
          words: [
            emptyWordTiming({
              text: 'Olá',
              startChar: 0,
              endChar: 3,
              startMs: null,
              endMs: null,
              needsReview: true,
              state: 'REVIEW',
            }),
          ],
        }),
      ),
    ).toBe(false);
  });

  it('hides failed synchronization', () => {
    expect(isReadyForStudent(content({ syncStatus: 'FAILED', availableToStudents: true }))).toBe(false);
  });
});
