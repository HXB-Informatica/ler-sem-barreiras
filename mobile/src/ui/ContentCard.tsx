import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radii } from '../theme';

type Props = {
  title: string;
  subtitle?: string;
  accessibilityLabel: string;
  onPress: () => void;
  accent?: string;
  badge?: ReactNode;
  footer?: ReactNode;
};

export function ContentCard({
  title,
  subtitle,
  accessibilityLabel,
  onPress,
  accent = colors.studentAccent,
  badge,
  footer,
}: Props) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={({ pressed }) => [styles.card, { borderLeftColor: accent, opacity: pressed ? 0.85 : 1 }]}>
      <View style={styles.body}>
        <Text style={styles.title}>{title}</Text>
        {badge}
        {subtitle ? <Text style={styles.sub}>{subtitle}</Text> : null}
      </View>
      {footer}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 22,
    minHeight: 88,
    borderLeftWidth: 8,
    gap: 10,
  },
  body: { gap: 8 },
  title: { fontSize: 22, fontWeight: '700', color: colors.text },
  sub: { fontSize: 16, color: colors.muted },
});
