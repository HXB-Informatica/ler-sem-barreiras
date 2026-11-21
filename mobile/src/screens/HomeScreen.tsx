import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Button } from '../ui/Button';
import { colors } from '../theme';

type Props = {
  onTeacher: () => void;
  onStudent: () => void;
  onAbout: () => void;
};

export function HomeScreen({ onTeacher, onStudent, onAbout }: Props) {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text style={styles.title}>Ler sem Barreiras</Text>
        <Text style={styles.lead}>
          Textos e narração sincronizados, no próprio tablet, sem internet.
        </Text>
        <Button label="Modo Professor" onPress={onTeacher} accessibilityLabel="Abrir modo professor" />
        <View style={styles.gap} />
        <Button label="Modo Aluno" onPress={onStudent} accessibilityLabel="Abrir modo aluno" />
        <View style={styles.gap} />
        <Button label="Sobre o projeto" onPress={onAbout} variant="secondary" />
        <Text style={styles.footer}>Escola Gil Vicente  ·  HXB, parceiro tecnológico</Text>
        <Text style={styles.version}>versão 0.2.0 — React Native</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: 28 },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', color: colors.text },
  lead: {
    fontSize: 18,
    textAlign: 'center',
    color: colors.muted,
    marginTop: 12,
    marginBottom: 36,
  },
  gap: { height: 16 },
  footer: { marginTop: 28, textAlign: 'center', color: colors.muted },
  version: { marginTop: 8, textAlign: 'center', color: colors.muted, fontSize: 12 },
});
