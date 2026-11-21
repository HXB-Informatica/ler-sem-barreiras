import { Pressable, StyleSheet, Text } from 'react-native';
import { colors } from '../theme';

type Props = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  accessibilityLabel?: string;
  variant?: 'primary' | 'secondary' | 'danger';
};

export function Button({ label, onPress, disabled, accessibilityLabel, variant = 'primary' }: Props) {
  const background =
    variant === 'secondary' ? colors.secondary : variant === 'danger' ? colors.danger : colors.primary;
  const color = variant === 'secondary' ? colors.text : colors.primaryText;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      disabled={disabled}
      onPress={onPress}
      style={[styles.button, { backgroundColor: background, opacity: disabled ? 0.5 : 1 }]}>
      <Text style={[styles.label, { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 56,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  label: {
    fontSize: 18,
    fontWeight: '600',
  },
});
