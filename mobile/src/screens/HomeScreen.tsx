import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { PROJECT } from '../about/projectInfo';
import { colors, spacing, type } from '../theme';
import { BrandMark } from '../ui/BrandMark';
import { Button } from '../ui/Button';

type Props = {
  onTeacher: () => void;
  onStudent: () => void;
  onAbout: () => void;
};

export function HomeScreen({ onTeacher, onStudent, onAbout }: Props) {
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <BrandMark size={128} />
        <Text style={styles.title} accessibilityRole="header">
          {PROJECT.name}
        </Text>
        <Text style={styles.lead}>{PROJECT.tagline}</Text>
        <Button
          label="Modo Aluno"
          variant="student"
          onPress={onStudent}
          accessibilityLabel="Abrir modo aluno, para ler e ouvir textos"
        />
        <View style={styles.gap} />
        <Button
          label="Modo Professor"
          variant="teacher"
          onPress={onTeacher}
          accessibilityLabel="Abrir modo professor, para criar e gerir conteúdos"
        />
        <View style={styles.gapWide} />
        <Button label="Sobre o projeto" onPress={onAbout} variant="ghost" />
        <Text style={styles.footer}>
          {PROJECT.schoolShort} · {PROJECT.schoolRole}
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 28,
    paddingVertical: spacing.xl,
    gap: 4,
  },
  title: { ...type.title, textAlign: 'center', marginTop: spacing.md },
  lead: {
    ...type.body,
    textAlign: 'center',
    color: colors.muted,
    marginTop: 12,
    marginBottom: 36,
  },
  gap: { height: 16 },
  gapWide: { height: 28 },
  footer: { marginTop: 28, textAlign: 'center', color: colors.muted, fontSize: 15, lineHeight: 22 },
});
