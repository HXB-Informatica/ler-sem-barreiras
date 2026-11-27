import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors, type } from '../theme';

type Props = {
  title: string;
  message: string;
  illustration?: ReactNode;
  action?: ReactNode;
};

export function EmptyState({ title, message, illustration, action }: Props) {
  return (
    <View style={styles.wrap} accessibilityRole="summary">
      {illustration}
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.message}>{message}</Text>
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 28,
    gap: 12,
  },
  title: { ...type.heading, textAlign: 'center' },
  message: { ...type.body, textAlign: 'center', color: colors.muted, maxWidth: 420 },
});
