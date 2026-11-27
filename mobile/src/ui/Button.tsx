import { Pressable, StyleSheet, Text } from 'react-native';
import { colors, radii } from '../theme';

type Props = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  accessibilityLabel?: string;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'student' | 'teacher';
};

export function Button({ label, onPress, disabled, accessibilityLabel, variant = 'primary' }: Props) {
  const background =
    variant === 'secondary'
      ? colors.secondary
      : variant === 'danger'
        ? colors.danger
        : variant === 'ghost'
          ? 'transparent'
          : variant === 'student'
            ? colors.studentAccent
            : variant === 'teacher'
              ? colors.teacherAccent
              : colors.primary;
  const color =
    variant === 'secondary' || variant === 'ghost' ? colors.primaryDeep : colors.primaryText;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.button,
        { backgroundColor: background, opacity: disabled ? 0.5 : 1 },
        variant === 'ghost' ? styles.ghost : null,
      ]}>
      <Text style={[styles.label, { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 56,
    borderRadius: radii.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  ghost: {
    borderWidth: 1,
    borderColor: colors.sky,
    minHeight: 48,
  },
  label: {
    fontSize: 18,
    fontWeight: '700',
    textAlign: 'center',
  },
});
