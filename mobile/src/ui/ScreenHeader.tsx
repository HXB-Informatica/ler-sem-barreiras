import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { type } from '../theme';
import { Button } from './Button';

type Props = {
  title: string;
  onBack: () => void;
  onAbout?: () => void;
  extra?: ReactNode;
};

export function ScreenHeader({ title, onBack, onAbout, extra }: Props) {
  return (
    <View style={styles.header}>
      <View style={styles.row}>
        <View style={styles.flex}>
          <Button label="Voltar" onPress={onBack} variant="secondary" />
        </View>
        {onAbout ? (
          <View style={styles.flex}>
            <Button label="Sobre o projeto" onPress={onAbout} variant="ghost" />
          </View>
        ) : null}
      </View>
      <Text style={type.heading}>{title}</Text>
      {extra}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { padding: 16, gap: 12 },
  row: { flexDirection: 'row', gap: 8 },
  flex: { flex: 1 },
});
