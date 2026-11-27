import { useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { PresentationMode, ReadingContent } from '../core/models';
import { currentWordIndex } from '../core/timeValidator';
import { audioBridge } from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { colors } from '../theme';
import { AppHeader } from '../ui/AppHeader';
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
  const fontSize = 24 * FONT_STEPS[fontIndex];

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
      <View style={styles.pad}>
        <AppHeader title={content?.title ?? 'Leitura'} onBack={onBack} />
      </View>
      <ScrollView contentContainerStyle={styles.surface} accessibilityLiveRegion="polite">
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
          <ModeChip
            label="Texto"
            active={mode === 'FULL_TEXT'}
            onPress={() => setMode('FULL_TEXT')}
            accessibilityLabel="Modo texto completo"
          />
          <ModeChip
            label="Palavra"
            active={mode === 'SINGLE_WORD'}
            onPress={() => setMode('SINGLE_WORD')}
            accessibilityLabel="Modo uma palavra de cada vez"
          />
          <ModeChip
            label="Progressiva"
            active={mode === 'PROGRESSIVE'}
            onPress={() => setMode('PROGRESSIVE')}
            accessibilityLabel="Modo leitura progressiva"
          />
        </View>
        <View style={styles.row}>
          <View style={styles.flex}>
            <Button
              label="Letra"
              onPress={() => setFontIndex(index => (index + 1) % FONT_STEPS.length)}
              variant="secondary"
              accessibilityLabel={`Aumentar letras. Tamanho ${fontIndex + 1} de ${FONT_STEPS.length}`}
            />
          </View>
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
            <Button
              label={playing ? 'Pausar' : 'Ouvir'}
              onPress={playing ? pause : play}
              disabled={missingAudio}
              accessibilityLabel={playing ? 'Pausar a narração' : 'Ouvir a narração'}
            />
          </View>
          <View style={styles.flex}>
            <Button
              label="Recomeçar"
              onPress={restart}
              disabled={missingAudio}
              variant="secondary"
              accessibilityLabel="Recomeçar a narração do início"
            />
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}

function ModeChip({
  label,
  active,
  onPress,
  accessibilityLabel,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  accessibilityLabel: string;
}) {
  return (
    <View style={styles.flex}>
      <Button
        label={label}
        onPress={onPress}
        variant={active ? 'primary' : 'secondary'}
        accessibilityLabel={`${accessibilityLabel}${active ? ', seleccionado' : ''}`}
      />
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
      <Text style={{ color: foreground, fontSize, lineHeight: fontSize * 1.5, textAlign: 'center' }}>
        {content.text}
      </Text>
    );
  }
  if (mode === 'SINGLE_WORD') {
    const word = words[Math.max(0, currentIndex)]?.text ?? content.text;
    return (
      <Text
        accessibilityLabel={`Palavra actual: ${word}`}
        style={{
          color: foreground,
          fontSize: fontSize + 10,
          fontWeight: '700',
          textAlign: 'center',
          backgroundColor: highlight,
          overflow: 'hidden',
          padding: 12,
        }}>
        {word}
      </Text>
    );
  }
  const visible = mode === 'PROGRESSIVE' ? words.slice(0, Math.max(1, currentIndex + 1)) : words;
  return (
    <Text style={{ color: foreground, fontSize, lineHeight: fontSize * 1.5, textAlign: 'center' }}>
      {visible.map((word, index) => {
        const active = index === currentIndex;
        return (
          <Text
            key={word.id || `${index}-${word.startChar}`}
            accessibilityLabel={active ? `Palavra actual: ${word.text}` : undefined}
            style={
              active
                ? {
                    backgroundColor: highlight,
                    color: '#111111',
                    fontWeight: '800',
                    textDecorationLine: 'underline',
                  }
                : undefined
            }>
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
  pad: { paddingHorizontal: 12 },
  surface: { paddingHorizontal: 20, paddingBottom: 16, flexGrow: 1, justifyContent: 'center' },
  controls: { padding: 12, gap: 10 },
  row: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  flex: { flex: 1 },
  contrast: { flexDirection: 'row', alignItems: 'center', minHeight: 48 },
  err: { color: colors.danger },
});
