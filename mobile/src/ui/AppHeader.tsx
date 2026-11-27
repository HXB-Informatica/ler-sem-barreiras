import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, type } from '../theme';

type Props = {
  title: string;
  onBack: () => void;
  onAbout?: () => void;
  accessibilityBackLabel?: string;
};

export function AppHeader({ title, onBack, onAbout, accessibilityBackLabel = 'Voltar' }: Props) {
  return (
    <View style={styles.bar}>
      <Pressable
        onPress={onBack}
        accessibilityRole="button"
        accessibilityLabel={accessibilityBackLabel}
        hitSlop={8}
        style={styles.iconBtn}>
        <Text style={styles.icon}>←</Text>
      </Pressable>
      <Text style={styles.title} numberOfLines={2} accessibilityRole="header">
        {title}
      </Text>
      {onAbout ? (
        <Pressable
          onPress={onAbout}
          accessibilityRole="button"
          accessibilityLabel="Sobre o projeto"
          hitSlop={8}
          style={styles.iconBtn}>
          <Text style={styles.icon}>i</Text>
        </Pressable>
      ) : (
        <View style={styles.iconBtn} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 56,
    paddingVertical: 4,
    gap: 8,
  },
  iconBtn: {
    minWidth: 48,
    minHeight: 48,
    borderRadius: 24,
    backgroundColor: colors.secondary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    fontSize: 22,
    fontWeight: '700',
    color: colors.primaryDeep,
  },
  title: {
    ...type.heading,
    flex: 1,
    fontSize: 20,
    textAlign: 'center',
  },
});
