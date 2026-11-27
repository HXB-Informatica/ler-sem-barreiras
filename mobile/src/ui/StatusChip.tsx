import { StyleSheet, Text, View } from 'react-native';
import { syncLabel } from '../core/lsb';
import type { SyncStatus } from '../core/models';
import { colors, radii } from '../theme';

type Props = {
  status: SyncStatus;
};

const TONE: Record<SyncStatus, { bg: string; fg: string }> = {
  NONE: { bg: '#E5E7EB', fg: colors.text },
  IN_PROGRESS: { bg: '#D7F6F2', fg: colors.success },
  COMPLETED: { bg: '#D7F6F2', fg: colors.success },
  COMPLETED_WITH_GAPS: { bg: colors.warningBg, fg: '#92400E' },
  FAILED: { bg: '#FEE4E2', fg: colors.danger },
  CANCELLED: { bg: '#E5E7EB', fg: colors.muted },
};

export function StatusChip({ status }: Props) {
  const tone = TONE[status];
  const label = syncLabel(status);
  return (
    <View
      style={[styles.chip, { backgroundColor: tone.bg }]}
      accessibilityRole="text"
      accessibilityLabel={`Estado: ${label}`}>
      <Text style={[styles.label, { color: tone.fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    alignSelf: 'flex-start',
    borderRadius: radii.sm,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: '700',
  },
});
