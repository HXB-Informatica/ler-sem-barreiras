import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { align } from '../core/alignmentEngine';
import { LsbException } from '../core/lsbErrors';
import {
  alignmentStatus,
  isReadyForStudent,
  requiresReview,
  type ReadingContent,
  type WordTiming,
} from '../core/models';
import {
  absolutePath,
  audioBridge,
  pickFile,
  requestMicrophone,
  transcribeLocal,
} from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { randomUuid } from '../core/uuid';
import { ensure } from '../core/wordIdentity';
import { colors, radii } from '../theme';
import { AppHeader } from '../ui/AppHeader';
import { Button } from '../ui/Button';
import { useLayout } from '../ui/layout';
import { RecordingMeter } from '../ui/RecordingMeter';
import { ScreenShell } from '../ui/ScreenShell';
import { wavDurationMs } from '../core/wavProbe';
import { bytesFromBase64 } from '../core/sha256';

type Props = {
  repository: ContentRepository;
  contentId: string | null;
  onBack: () => void;
};

type Step = 1 | 2 | 3 | 4;

export function TeacherEditScreen({ repository, contentId, onBack }: Props) {
  const layout = useLayout();
  const [id] = useState(contentId ?? randomUuid());
  const [step, setStep] = useState<Step>(1);
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const [words, setWords] = useState<WordTiming[]>([]);
  const [hasAudio, setHasAudio] = useState(false);
  const [durationMs, setDurationMs] = useState(0);
  const [recording, setRecording] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdAt, setCreatedAt] = useState(Date.now());
  const [playing, setPlaying] = useState(false);
  const [availableToStudents, setAvailableToStudents] = useState<boolean | undefined>(undefined);
  const [loaded, setLoaded] = useState(!contentId);
  const snapshot = useRef('');
  const busy = useRef(false);

  const fingerprint = () =>
    JSON.stringify({
      title,
      text,
      hasAudio,
      durationMs,
      availableToStudents,
      words: words.map(word => [word.startMs, word.endMs, word.manuallyAdjusted]),
    });

  const load = useCallback(async () => {
    if (!contentId) {
      snapshot.current = JSON.stringify({
        title: '',
        text: '',
        hasAudio: false,
        durationMs: 0,
        availableToStudents: undefined,
        words: [],
      });
      return;
    }
    const existing = await repository.get(contentId);
    if (!existing) {
      setLoaded(true);
      return;
    }
    setTitle(existing.title);
    setText(existing.text);
    setWords(existing.words);
    setDurationMs(existing.durationMs);
    setCreatedAt(existing.createdAtEpochMs);
    setHasAudio(Boolean(existing.audioRelativePath));
    setAvailableToStudents(existing.availableToStudents);
    snapshot.current = JSON.stringify({
      title: existing.title,
      text: existing.text,
      hasAudio: Boolean(existing.audioRelativePath),
      durationMs: existing.durationMs,
      availableToStudents: existing.availableToStudents,
      words: existing.words.map(word => [word.startMs, word.endMs, word.manuallyAdjusted]),
    });
    setLoaded(true);
  }, [contentId, repository]);

  useEffect(() => {
    load().catch(err => setError(messageOf(err)));
  }, [load]);

  useEffect(() => {
    if (loaded && !snapshot.current) {
      snapshot.current = fingerprint();
    }
  }, [loaded, title, text, hasAudio]);

  useEffect(() => {
    return () => {
      audioBridge.stopRecording().catch(() => undefined);
      audioBridge.stop().catch(() => undefined);
    };
  }, []);

  useEffect(() => {
    if (!playing) {
      return;
    }
    const timer = setInterval(() => {
      audioBridge
        .status()
        .then(status => {
          if (!status.playing) {
            setPlaying(false);
          }
        })
        .catch(() => undefined);
    }, 200);
    return () => clearInterval(timer);
  }, [playing]);

  const current = (overrides: Partial<ReadingContent> = {}): ReadingContent => ({
    id,
    title: title.trim() || 'Sem título',
    text,
    audioRelativePath: hasAudio ? `contents/${id}/audio.wav` : null,
    durationMs,
    words: ensure(text, words),
    syncStatus:
      words.length === 0
        ? 'NONE'
        : alignmentStatus({
            words,
            alignedCount: words.filter(word => word.startMs != null).length,
            reviewCount: words.filter(requiresReview).length,
            unusedRecognizedCount: 0,
            intervalIssues: [],
          }),
    createdAtEpochMs: createdAt,
    updatedAtEpochMs: Date.now(),
    availableToStudents,
    ...overrides,
  });

  const persist = async (overrides: Partial<ReadingContent> = {}, audioBase64?: string | null) => {
    const stored = await repository.save(current(overrides), audioBase64);
    setHasAudio(Boolean(stored.audioRelativePath));
    setAvailableToStudents(stored.availableToStudents);
    snapshot.current = JSON.stringify({
      title: stored.title,
      text: stored.text,
      hasAudio: Boolean(stored.audioRelativePath),
      durationMs: stored.durationMs,
      availableToStudents: stored.availableToStudents,
      words: stored.words.map(word => [word.startMs, word.endMs, word.manuallyAdjusted]),
    });
    setError(null);
    return stored;
  };

  const saveDraft = async () => {
    try {
      await persist({ availableToStudents: false });
      setAvailableToStudents(false);
      setMessage('Rascunho guardado neste tablet.');
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const publish = async () => {
    const draft = current();
    if (!draft.text.trim() || !hasAudio) {
      setError('É preciso texto e narração para disponibilizar a leitura.');
      return;
    }
    if (draft.syncStatus !== 'COMPLETED' && draft.syncStatus !== 'COMPLETED_WITH_GAPS') {
      setError('Sincronize a narração antes de disponibilizar aos alunos.');
      return;
    }
    try {
      await persist({ availableToStudents: true });
      setAvailableToStudents(true);
      setMessage('Conteúdo disponível no Modo Aluno.');
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const toggleRecord = async () => {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      if (recording) {
        const result = await audioBridge.stopRecording();
        setRecording(false);
        setDurationMs(result.durationMs);
        setHasAudio(true);
        setMessage('Narração gravada.');
        return;
      }
      const granted = await requestMicrophone();
      if (!granted) {
        setError('É necessária permissão de microfone para gravar.');
        return;
      }
      await audioBridge.stop().catch(() => undefined);
      await audioBridge.startRecording(`contents/${id}/audio.wav`);
      setRecording(true);
      setMessage(null);
      setError(null);
    } catch (err) {
      setRecording(false);
      setError(messageOf(err));
    } finally {
      busy.current = false;
    }
  };

  const importAudio = async () => {
    try {
      const picked = await pickFile('audio/*');
      if (!picked) {
        return;
      }
      const bytes = bytesFromBase64(picked);
      const duration = wavDurationMs(bytes);
      await persist({}, picked);
      setDurationMs(duration);
      setHasAudio(true);
      setMessage('Áudio importado.');
      setError(null);
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const togglePlayback = async () => {
    try {
      if (playing) {
        await audioBridge.pause();
        setPlaying(false);
        return;
      }
      await audioBridge.play(`contents/${id}/audio.wav`);
      setPlaying(true);
      setError(null);
    } catch (err) {
      setPlaying(false);
      setError(messageOf(err));
    }
  };

  const restartPlayback = async () => {
    try {
      await audioBridge.seek(0);
      await audioBridge.play(`contents/${id}/audio.wav`);
      setPlaying(true);
      setError(null);
    } catch (err) {
      setPlaying(false);
      setError(messageOf(err));
    }
  };

  const synchronize = async () => {
    try {
      setSyncing(true);
      setError(null);
      setMessage(null);
      if (!hasAudio) {
        throw new Error('Grave ou importe a narração antes de sincronizar.');
      }
      const path = await absolutePath(`contents/${id}/audio.wav`);
      const transcription = await transcribeLocal(path);
      const result = align({
        sourceText: text,
        transcription,
        audioDurationMs: durationMs,
        previousWords: words,
      });
      const status = alignmentStatus(result);
      setWords(result.words);
      await persist({ words: result.words, syncStatus: status });
      if (status === 'FAILED') {
        setError('A sincronização não foi concluída. Reveja o texto e a narração.');
        setMessage(null);
        return;
      }
      setMessage(
        result.reviewCount > 0
          ? `Sincronizado. ${result.reviewCount} palavras precisam de revisão.`
          : 'Sincronização concluída.',
      );
    } catch (err) {
      setError(messageOf(err));
      setMessage(null);
    } finally {
      setSyncing(false);
    }
  };

  const requestBack = () => {
    if (recording) {
      Alert.alert('A gravar', 'Pare a gravação antes de sair.');
      return;
    }
    if (fingerprint() !== snapshot.current) {
      Alert.alert('Sair sem guardar?', 'Há alterações que ainda não foram guardadas.', [
        { text: 'Continuar a editar', style: 'cancel' },
        { text: 'Sair', style: 'destructive', onPress: onBack },
      ]);
      return;
    }
    onBack();
  };

  const draft = current();
  const canPublish =
    Boolean(text.trim()) &&
    hasAudio &&
    (draft.syncStatus === 'COMPLETED' || draft.syncStatus === 'COMPLETED_WITH_GAPS');

  return (
    <ScreenShell>
      <AppHeader title="Preparar conteúdo" onBack={requestBack} />
      <StepTabs step={step} onChange={setStep} />
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.body}>
        {step === 1 ? (
          <View style={styles.block}>
            <Text style={styles.label}>Título</Text>
            <TextInput value={title} onChangeText={setTitle} style={styles.input} accessibilityLabel="Título" />
            <Text style={styles.label}>Texto para os alunos</Text>
            <TextInput
              value={text}
              onChangeText={setText}
              style={[styles.input, styles.multiline]}
              multiline
              accessibilityLabel="Texto para os alunos"
            />
            <Button label="Continuar para a narração" onPress={() => setStep(2)} disabled={!text.trim()} />
          </View>
        ) : null}
        {step === 2 ? (
          <View style={layout.isTablet && layout.isLandscape ? styles.split : styles.block}>
            <View style={styles.flex}>
              <RecordingMeter recording={recording} hasAudio={hasAudio} durationMs={durationMs} />
            </View>
            <View style={styles.flex}>
              <Button
                label={recording ? 'Parar gravação' : 'Gravar'}
                onPress={toggleRecord}
                variant={recording ? 'danger' : 'primary'}
                accessibilityLabel={recording ? 'Parar gravação' : 'Começar a gravar a narração'}
              />
              <View style={styles.gap} />
              <Button label="Importar áudio" onPress={importAudio} disabled={recording} variant="secondary" />
              {hasAudio ? (
                <>
                  <View style={styles.gap} />
                  <Button
                    label={playing ? 'Parar' : 'Reproduzir'}
                    onPress={togglePlayback}
                    variant="secondary"
                  />
                  <View style={styles.gap} />
                  <Button label="Ouvir novamente" onPress={restartPlayback} variant="secondary" />
                  <View style={styles.gap} />
                  <Button
                    label="Regravar"
                    onPress={toggleRecord}
                    variant="ghost"
                    disabled={recording}
                    accessibilityLabel="Substituir a narração com uma gravação nova"
                  />
                </>
              ) : null}
              <View style={styles.gap} />
              <Button label="Continuar" onPress={() => setStep(3)} disabled={!hasAudio || recording} />
            </View>
          </View>
        ) : null}
        {step === 3 ? (
          <View style={styles.block}>
            <Button
              label={syncing ? 'A sincronizar…' : 'Sincronizar automaticamente'}
              onPress={synchronize}
              disabled={syncing || recording || !text.trim() || !hasAudio}
            />
            {syncing ? <Text style={styles.hint}>A processar neste tablet. Não fecha a aplicação.</Text> : null}
            {message ? <Text style={styles.ok}>{message}</Text> : null}
            {error && step === 3 ? <Text style={styles.err}>{error}</Text> : null}
            {words.length > 0 ? (
              <View style={styles.words}>
                <Text style={styles.section}>Resultado</Text>
                <Text style={styles.hint}>
                  {words.filter(requiresReview).length} palavras para rever · {words.filter(word => word.startMs != null).length} com tempo
                </Text>
                {words.map((word, index) => (
                  <WordTimeRow
                    key={word.id || `${word.index}-${word.startChar}`}
                    word={word}
                    onApply={(startMs, endMs) => {
                      setWords(previous =>
                        previous.map((item, itemIndex) =>
                          itemIndex === index
                            ? {
                                ...item,
                                startMs,
                                endMs,
                                manuallyAdjusted: true,
                                needsReview: false,
                                state: 'MANUAL',
                                timestampOrigin: 'MANUAL',
                              }
                            : item,
                        ),
                      );
                    }}
                  />
                ))}
                <Button label={playing ? 'Pausar narração' : 'Ouvir narração'} onPress={togglePlayback} variant="secondary" />
              </View>
            ) : null}
            <Button label="Continuar para guardar" onPress={() => setStep(4)} />
          </View>
        ) : null}
        {step === 4 ? (
          <View style={styles.block}>
            <Text style={styles.hint}>
              O rascunho fica só no Modo Professor. Disponibilizar para leitura exige texto, narração e
              sincronização concluída.
            </Text>
            <Text style={styles.meta}>
              {isReadyForStudent({ ...draft, availableToStudents: availableToStudents ?? false })
                ? 'Estado: visível no Modo Aluno'
                : 'Estado: rascunho'}
            </Text>
            <Button label="Guardar rascunho" onPress={saveDraft} variant="secondary" />
            <View style={styles.gap} />
            <Button
              label="Disponibilizar para leitura"
              onPress={publish}
              disabled={!canPublish}
              accessibilityLabel="Disponibilizar para leitura no modo aluno"
            />
            {!canPublish ? (
              <Text style={styles.hint}>Ainda faltam a narração ou a sincronização.</Text>
            ) : null}
          </View>
        ) : null}
        {error && step !== 3 ? <Text style={styles.err}>{error}</Text> : null}
        {message && step !== 3 ? <Text style={styles.ok}>{message}</Text> : null}
      </ScrollView>
    </ScreenShell>
  );
}

function WordTimeRow({
  word,
  onApply,
}: {
  word: WordTiming;
  onApply: (startMs: number, endMs: number) => void;
}) {
  const [start, setStart] = useState(word.startMs != null ? String(word.startMs) : '');
  const [end, setEnd] = useState(word.endMs != null ? String(word.endMs) : '');
  const review = requiresReview(word);
  return (
    <View style={styles.wordRow}>
      <Text style={[styles.word, review ? styles.wordReview : null]}>
        {word.text}
        {review ? ' — rever' : word.manuallyAdjusted ? ' — corrigido' : ''}
      </Text>
      <View style={styles.timeFields}>
        <TextInput
          value={start}
          onChangeText={setStart}
          keyboardType="number-pad"
          style={styles.timeInput}
          accessibilityLabel={`Início de ${word.text} em milissegundos`}
        />
        <TextInput
          value={end}
          onChangeText={setEnd}
          keyboardType="number-pad"
          style={styles.timeInput}
          accessibilityLabel={`Fim de ${word.text} em milissegundos`}
        />
        <Button
          label="Aplicar"
          variant="secondary"
          onPress={() => {
            const startMs = Number.parseInt(start, 10);
            const endMs = Number.parseInt(end, 10);
            if (Number.isNaN(startMs) || Number.isNaN(endMs) || endMs < startMs) {
              return;
            }
            onApply(startMs, endMs);
          }}
          accessibilityLabel={`Aplicar tempos de ${word.text}`}
        />
      </View>
    </View>
  );
}

function StepTabs({ step, onChange }: { step: Step; onChange: (step: Step) => void }) {
  const items: { id: Step; label: string }[] = [
    { id: 1, label: '1 Texto' },
    { id: 2, label: '2 Narração' },
    { id: 3, label: '3 Sincronizar' },
    { id: 4, label: '4 Guardar' },
  ];
  return (
    <View style={styles.tabs}>
      {items.map(item => (
        <View key={item.id} style={styles.tab}>
          <Button
            label={item.label}
            onPress={() => onChange(item.id)}
            variant={step === item.id ? 'primary' : 'secondary'}
            accessibilityLabel={`${item.label}${step === item.id ? ', etapa actual' : ''}`}
          />
        </View>
      ))}
    </View>
  );
}

function messageOf(error: unknown): string {
  if (error instanceof LsbException) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Ocorreu um erro inesperado.';
}

const styles = StyleSheet.create({
  body: { paddingBottom: 40, gap: 12 },
  block: { gap: 12 },
  split: { flexDirection: 'row', gap: 16, alignItems: 'flex-start' },
  flex: { flex: 1, gap: 0 },
  label: { color: colors.muted, fontWeight: '700' },
  input: {
    backgroundColor: colors.surface,
    borderRadius: radii.sm,
    padding: 12,
    fontSize: 18,
    color: colors.text,
    minHeight: 48,
  },
  multiline: { minHeight: 180, textAlignVertical: 'top' },
  section: { fontSize: 20, fontWeight: '700', color: colors.text },
  hint: { fontSize: 16, lineHeight: 24, color: colors.muted },
  meta: { fontSize: 16, fontWeight: '700', color: colors.text },
  ok: { color: colors.primary, fontSize: 16 },
  err: { color: colors.danger, fontSize: 16 },
  words: { gap: 8 },
  word: { fontSize: 16, color: colors.text, fontWeight: '700' },
  wordReview: { color: colors.danger },
  wordRow: { gap: 6, paddingVertical: 6, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.secondary },
  timeFields: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center' },
  timeInput: {
    minHeight: 48,
    minWidth: 88,
    backgroundColor: colors.surface,
    borderRadius: radii.sm,
    paddingHorizontal: 10,
    fontSize: 16,
    color: colors.text,
  },
  gap: { height: 10 },
  tabs: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 },
  tab: { flexGrow: 1, flexBasis: '47%' },
});
