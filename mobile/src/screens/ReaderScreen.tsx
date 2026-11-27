import { useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { PresentationMode, ReadingContent } from '../core/models';
import { currentWordIndex } from '../core/timeValidator';
import { audioBridge } from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { colors } from '../theme';
import { Button } from '../ui/Button';

type Props = {
  repository: ContentRepository;
  contentId: string;
  onBack: () => void;
};

const FONT_STEPS = [1.1, 1.4, 1.8, 2.2];

export function ReaderScreen({ repository, contentId, onBack }: Props) {
  const [content, setContent] = useState<ReadingContent | null>(null);
  const [mode, setMode] = useState<PresentationMode>('FULL_TEXT');
  const [fontIndex, setFontIndex] = useState(1);
  const [highContrast, setHighContrast] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [positionMs, setPositionMs] = useState(0);
  const [missingAudio, setMissingAudio] = useState(false);

  useEffect(() => {
    repository.get(contentId).then(item => {
      setContent(item);
      setMissingAudio(!item?.audioRelativePath);
    });
  }, [contentId, repository]);

  useEffect(() => {
    return () => {
      audioBridge.stop().catch(() => undefined);
    };
  }, []);

  useEffect(() => {
    if (!playing) {
      return;
    }
    const timer = setInterval(() => {
      audioBridge.status().then(status => {
        setPositionMs(status.positionMs);
        if (!status.playing) {
          setPlaying(false);
        }
      });
    }, 80);
    return () => clearInterval(timer);
  }, [playing]);

  const wordIndex = useMemo(
    () => (content ? currentWordIndex(content.words, positionMs) : -1),
    [content, positionMs],
  );

  const foreground = highContrast ? colors.contrastFg : colors.text;
  const background = highContrast ? colors.contrastBg : colors.background;
  const highlight = highContrast ? colors.contrastHighlight : colors.yellow;
  const fontSize = 22 * FONT_STEPS[fontIndex];

  const play = async () => {
    if (!content?.audioRelativePath) {
      return;
    }
    try {
      await audioBridge.play(content.audioRelativePath);
      setPlaying(true);
    } catch (error) {
      setPlaying(false);
      setMissingAudio(true);
      Alert.alert('Narração', error instanceof Error ? error.message : 'Não foi possível ouvir a narração.');
    }
  };

  const pause = async () => {
    await audioBridge.pause();
    setPlaying(false);
  };

  const restart = async () => {
    if (!content?.audioRelativePath) {
      return;
    }
    await audioBridge.seek(0);
    await audioBridge.play(content.audioRelativePath);
    setPositionMs(0);
    setPlaying(true);
  };

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: background }]}>
      <View style={styles.header}>
        <Button label="Voltar" onPress={onBack} variant="secondary" />
        <Text style={[styles.title, { color: foreground }]}>{content?.title ?? 'Leitura'}</Text>
      </View>
      <ScrollView contentContainerStyle={styles.surface}>
        {content ? (
          <ReadingSurface
            content={content}
            mode={mode}
            currentIndex={wordIndex}
            fontSize={fontSize}
            foreground={foreground}
            highlight={highlight}
          />
        ) : (
          <Text style={{ color: foreground }}>A carregar…</Text>
        )}
      </ScrollView>
      <View style={styles.controls}>
        <View style={styles.row}>
          <ModeChip label="Texto" active={mode === 'FULL_TEXT'} onPress={() => setMode('FULL_TEXT')} />
          <ModeChip label="Palavra" active={mode === 'SINGLE_WORD'} onPress={() => setMode('SINGLE_WORD')} />
          <ModeChip label="Progressiva" active={mode === 'PROGRESSIVE'} onPress={() => setMode('PROGRESSIVE')} />
        </View>
        <View style={styles.row}>
          <Button label="Letra" onPress={() => setFontIndex(index => (index + 1) % FONT_STEPS.length)} variant="secondary" />
          <View style={styles.contrast}>
            <Text style={{ color: foreground, marginRight: 8 }}>Contraste</Text>
            <Switch
              value={highContrast}
              onValueChange={setHighContrast}
              accessibilityLabel="Contraste elevado"
            />
          </View>
        </View>
        {missingAudio ? (
          <Text style={styles.err}>Este conteúdo não tem áudio neste tablet.</Text>
        ) : null}
        <View style={styles.row}>
          <View style={styles.flex}>
            <Button label={playing ? 'Pausar' : 'Ouvir'} onPress={playing ? pause : play} disabled={missingAudio} />
          </View>
          <View style={styles.flex}>
            <Button label="Recomeçar" onPress={restart} disabled={missingAudio} variant="secondary" />
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}

function ModeChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <View style={styles.flex}>
      <Button label={label} onPress={onPress} variant={active ? 'primary' : 'secondary'} />
    </View>
  );
}

function ReadingSurface({
  content,
  mode,
  currentIndex,
  fontSize,
  foreground,
  highlight,
}: {
  content: ReadingContent;
  mode: PresentationMode;
  currentIndex: number;
  fontSize: number;
  foreground: string;
  highlight: string;
}) {
  const words = content.words;
  if (words.length === 0) {
    return (
      <Text style={{ color: foreground, fontSize, lineHeight: fontSize * 1.45 }}>{content.text}</Text>
    );
  }
  if (mode === 'SINGLE_WORD') {
    const word = words[Math.max(0, currentIndex)]?.text ?? content.text;
    return (
      <Text style={{ color: foreground, fontSize: fontSize + 8, fontWeight: '700', textAlign: 'center' }}>
        {word}
      </Text>
    );
  }
  const visible = mode === 'PROGRESSIVE' ? words.slice(0, Math.max(1, currentIndex + 1)) : words;
  return (
    <Text style={{ color: foreground, fontSize, lineHeight: fontSize * 1.45 }}>
      {visible.map((word, index) => {
        const active = index === currentIndex;
        return (
          <Text
            key={word.id || `${index}-${word.startChar}`}
            style={active ? { backgroundColor: highlight, color: '#000000', fontWeight: '700' } : undefined}>
            {word.text}
            {index < visible.length - 1 ? ' ' : ''}
          </Text>
        );
      })}
    </Text>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { padding: 16, gap: 12 },
  title: { fontSize: 22, fontWeight: '700' },
  surface: { paddingHorizontal: 20, paddingBottom: 16, flexGrow: 1, justifyContent: 'center' },
  controls: { padding: 16, gap: 12 },
  row: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  flex: { flex: 1 },
  contrast: { flexDirection: 'row', alignItems: 'center' },
  err: { color: colors.danger },
});
