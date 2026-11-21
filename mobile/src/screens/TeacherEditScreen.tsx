import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { align } from '../core/alignmentEngine';
import { LsbException } from '../core/lsbErrors';
import { alignmentStatus, requiresReview, type ReadingContent, type WordTiming } from '../core/models';
import {
  absolutePath,
  audioBridge,
  pickFile,
  requestMicrophone,
  transcribeLocal,
  unzipWhitelisted,
} from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { randomUuid } from '../core/uuid';
import { ensure } from '../core/wordIdentity';
import { colors } from '../theme';
import { Button } from '../ui/Button';
import { RecordingMeter } from '../ui/RecordingMeter';
import { wavDurationMs } from '../core/wavProbe';
import { bytesFromBase64 } from '../core/sha256';

type Props = {
  repository: ContentRepository;
  contentId: string | null;
  onBack: () => void;
};

export function TeacherEditScreen({ repository, contentId, onBack }: Props) {
  const [id] = useState(contentId ?? randomUuid());
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

  const load = useCallback(async () => {
    if (!contentId) {
      return;
    }
    const existing = await repository.get(contentId);
    if (!existing) {
      return;
    }
    setTitle(existing.title);
    setText(existing.text);
    setWords(existing.words);
    setDurationMs(existing.durationMs);
    setCreatedAt(existing.createdAtEpochMs);
    setHasAudio(Boolean(existing.audioRelativePath));
  }, [contentId, repository]);

  useEffect(() => {
    load().catch(err => setError(messageOf(err)));
  }, [load]);

  const current = (): ReadingContent => ({
    id,
    title: title.trim() || 'Sem título',
    text,
    audioRelativePath: hasAudio ? `contents/${id}/audio.wav` : null,
    durationMs,
    words: ensure(text, words),
    syncStatus: words.length === 0 ? 'NONE' : alignmentStatus({
      words,
      alignedCount: words.filter(word => word.startMs != null).length,
      reviewCount: words.filter(requiresReview).length,
      unusedRecognizedCount: 0,
      intervalIssues: [],
    }),
    createdAtEpochMs: createdAt,
    updatedAtEpochMs: Date.now(),
  });

  const save = async (audioBase64?: string | null) => {
    try {
      const stored = await repository.save(current(), audioBase64);
      setHasAudio(Boolean(stored.audioRelativePath));
      setMessage('Conteúdo guardado neste tablet.');
      setError(null);
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const toggleRecord = async () => {
    try {
      if (recording) {
        const result = await audioBridge.stopRecording();
        setRecording(false);
        setDurationMs(result.durationMs);
        setHasAudio(true);
        setMessage('Narração gravada. Confirme a reprodução e, se possível, sincronize.');
        return;
      }
      const granted = await requestMicrophone();
      if (!granted) {
        setError('É necessária permissão de microfone para gravar.');
        return;
      }
      await audioBridge.startRecording(`contents/${id}/audio.wav`);
      setRecording(true);
      setMessage('A gravar neste tablet. Fale o texto e veja o gráfico a mexer.');
      setError(null);
    } catch (err) {
      setRecording(false);
      setError(messageOf(err));
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
      await repository.save(current(), picked);
      setDurationMs(duration);
      setHasAudio(true);
      setMessage('Áudio importado (WAV PCM).');
      setError(null);
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const importLsb = async () => {
    try {
      const picked = await pickFile('*/*');
      if (!picked) {
        return;
      }
      const files = await unzipWhitelisted(picked);
      const result = await repository.importLsbBase64Map(files);
      onBack();
      Alert.alert('Importação', result.duplicate ? 'Exercício já existente.' : 'Exercício importado.');
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
    } catch (err) {
      setError(messageOf(err));
    }
  };

  const synchronize = async () => {
    try {
      setSyncing(true);
      setError(null);
      if (!hasAudio) {
        throw new Error('Grave ou importe a narração antes de sincronizar.');
      }
      const path = await absolutePath(`contents/${id}/audio.wav`);
      const transcription = await transcribeLocal(path, text);
      const result = align({
        sourceText: text,
        transcription,
        audioDurationMs: durationMs,
        previousWords: words,
      });
      setWords(result.words);
      await repository.save({
        ...current(),
        words: result.words,
        syncStatus: alignmentStatus(result),
      });
      setMessage(
        result.reviewCount > 0
          ? `Sincronizado com ${result.reviewCount} palavras a rever.`
          : 'Sincronização concluída.',
      );
    } catch (err) {
      setError(messageOf(err));
    } finally {
      setSyncing(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.body}>
        <Button label="Voltar" onPress={onBack} variant="secondary" />
        <Text style={styles.title}>Preparar conteúdo</Text>
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
        <Text style={styles.section}>Narração</Text>
        <RecordingMeter recording={recording} />
        <View style={styles.row}>
          <View style={styles.flex}>
            <Button
              label={recording ? 'Parar gravação' : 'Gravar'}
              onPress={toggleRecord}
              variant={recording ? 'danger' : 'primary'}
              accessibilityLabel={recording ? 'Parar gravação' : 'Começar a gravar a narração'}
            />
          </View>
          <View style={styles.flex}>
            <Button label="Importar áudio" onPress={importAudio} disabled={recording} variant="secondary" />
          </View>
        </View>
        <View style={styles.row}>
          <View style={styles.flex}>
            <Button label={playing ? 'Pausar' : 'Reproduzir'} onPress={togglePlayback} disabled={!hasAudio} variant="secondary" />
          </View>
          <View style={styles.flex}>
            <Button
              label="Recomeçar"
              onPress={() => audioBridge.seek(0).then(() => audioBridge.play(`contents/${id}/audio.wav`))}
              disabled={!hasAudio}
              variant="secondary"
            />
          </View>
        </View>
        <Button
          label={syncing ? 'A sincronizar…' : 'Sincronizar automaticamente'}
          onPress={synchronize}
          disabled={syncing || recording || !text.trim() || !hasAudio}
        />
        <Button label="Importar exercício .lsb" onPress={importLsb} variant="secondary" />
        {message ? <Text style={styles.ok}>{message}</Text> : null}
        {error ? <Text style={styles.err}>{error}</Text> : null}
        {words.length > 0 ? (
          <View style={styles.words}>
            <Text style={styles.section}>Palavras alinhadas</Text>
            {words.map(word => (
              <Text key={word.id || `${word.index}-${word.startChar}`} style={styles.word}>
                {word.text}{' '}
                {word.startMs != null ? `${word.startMs}–${word.endMs} ms` : 'sem tempo'}
                {requiresReview(word) ? ' · rever' : ''}
              </Text>
            ))}
          </View>
        ) : null}
        <Button label="Guardar" onPress={() => save()} />
      </ScrollView>
    </SafeAreaView>
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
  safe: { flex: 1, backgroundColor: colors.background },
  body: { padding: 16, gap: 12, paddingBottom: 48 },
  title: { fontSize: 24, fontWeight: '700', color: colors.text },
  label: { color: colors.muted, fontWeight: '600' },
  input: {
    backgroundColor: colors.surface,
    borderRadius: 10,
    padding: 12,
    fontSize: 18,
    color: colors.text,
  },
  multiline: { minHeight: 140, textAlignVertical: 'top' },
  section: { fontSize: 20, fontWeight: '700', color: colors.text, marginTop: 8 },
  row: { flexDirection: 'row', gap: 12 },
  flex: { flex: 1 },
  ok: { color: colors.primary, fontSize: 16 },
  err: { color: colors.danger, fontSize: 16 },
  words: { gap: 4 },
  word: { fontSize: 16, color: colors.text },
});
