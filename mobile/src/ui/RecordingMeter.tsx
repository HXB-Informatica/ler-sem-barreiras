import { useEffect, useRef, useState } from 'react';
import { Animated, DeviceEventEmitter, StyleSheet, Text, View } from 'react-native';
import { audioBridge } from '../native/bridge';
import { colors } from '../theme';

const BAR_COUNT = 40;

type Props = {
  recording: boolean;
};

export function RecordingMeter({ recording }: Props) {
  const [bars, setBars] = useState<number[]>(() => Array.from({ length: BAR_COUNT }, () => 0.12));
  const [elapsedMs, setElapsedMs] = useState(0);
  const startedAt = useRef(Date.now());
  const pulse = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    if (!recording) {
      setElapsedMs(0);
      setBars(Array.from({ length: BAR_COUNT }, () => 0.12));
      return;
    }
    startedAt.current = Date.now();
    const pushLevel = (rms: number, peak: number, nativeElapsed?: number) => {
      // O emulador entrega o HyperX muito baixo (~5 % da escala). Sem este ganho as barras ficam paradas.
      const amplitude = Math.min(1, Math.max(peak * 14, rms * 36, 0.08));
      setElapsedMs(nativeElapsed && nativeElapsed > 0 ? nativeElapsed : Date.now() - startedAt.current);
      setBars(previous => [...previous.slice(1), amplitude]);
    };
    const subscription = DeviceEventEmitter.addListener('LsbAudioLevel', (event: { rms?: number; peak?: number; elapsedMs?: number }) => {
      pushLevel(event.rms ?? 0, event.peak ?? 0, event.elapsedMs);
    });
    const poll = setInterval(() => {
      setElapsedMs(Date.now() - startedAt.current);
      audioBridge
        .getLevel()
        .then(level => pushLevel(level.rms, level.peak, level.elapsedMs))
        .catch(() => undefined);
    }, 80);
    return () => {
      subscription.remove();
      clearInterval(poll);
    };
  }, [recording]);

  useEffect(() => {
    if (!recording) {
      pulse.stopAnimation();
      pulse.setValue(1);
      return;
    }
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 0.25, duration: 400, useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 400, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [recording, pulse]);

  return (
    <View
      style={[styles.card, recording ? styles.cardLive : styles.cardIdle]}
      accessibilityLiveRegion="polite"
      accessibilityLabel={
        recording ? `A gravar a narração, ${formatClock(elapsedMs)}` : 'O microfone ainda não está a gravar'
      }>
      <View style={styles.header}>
        <Animated.View style={[styles.dot, { opacity: pulse }]} />
        <Text style={[styles.status, recording ? styles.statusLive : styles.statusIdle]}>
          {recording ? 'A GRAVAR' : 'Pronto a gravar'}
        </Text>
        <Text style={styles.clock}>{formatClock(elapsedMs)}</Text>
      </View>
      <View style={styles.wave}>
        {bars.map((value, index) => (
          <View
            key={index}
            style={[
              styles.bar,
              {
                height: 10 + value * 78,
                backgroundColor: recording ? colors.danger : colors.primary,
                opacity: recording ? 0.45 + value * 0.55 : 0.28,
              },
            ]}
          />
        ))}
      </View>
      <Text style={styles.hint}>
        {recording
          ? 'Fale agora. O ponto vermelho pisca e as barras sobem com a sua voz.'
          : 'Toque em Gravar: aparece «A GRAVAR», o tempo e um gráfico do microfone.'}
      </Text>
    </View>
  );
}

function formatClock(elapsedMs: number): string {
  const total = Math.max(0, Math.floor(elapsedMs / 1000));
  const minutes = Math.floor(total / 60)
    .toString()
    .padStart(2, '0');
  const seconds = (total % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: 14,
    gap: 10,
    borderWidth: 2,
    minHeight: 170,
  },
  cardLive: {
    backgroundColor: '#FDECEC',
    borderColor: colors.danger,
  },
  cardIdle: {
    backgroundColor: colors.surface,
    borderColor: colors.secondary,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  dot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.danger,
  },
  status: {
    flex: 1,
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: 0.6,
  },
  statusLive: { color: colors.danger },
  statusIdle: { color: colors.muted },
  clock: {
    fontSize: 24,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    color: colors.text,
  },
  wave: {
    height: 92,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 2,
  },
  bar: {
    flex: 1,
    borderRadius: 2,
    minHeight: 8,
  },
  hint: {
    fontSize: 15,
    color: colors.muted,
  },
});
