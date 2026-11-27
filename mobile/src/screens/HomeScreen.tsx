import { StyleSheet, Text, View } from 'react-native';
import { PROJECT } from '../about/projectInfo';
import { colors, spacing, type } from '../theme';
import { BrandMark } from '../ui/BrandMark';
import { Button } from '../ui/Button';
import { useLayout } from '../ui/layout';
import { ScreenShell } from '../ui/ScreenShell';

type Props = {
  onTeacher: () => void;
  onStudent: () => void;
  onAbout: () => void;
};

export function HomeScreen({ onTeacher, onStudent, onAbout }: Props) {
  const layout = useLayout();
  const split = layout.isLandscape;
  const logo = split ? 112 : layout.isTablet ? 148 : 120;
  return (
    <ScreenShell scroll>
      <View style={[styles.container, layout.isLandscape && styles.landscape, split && styles.split]}>
        <View style={split ? styles.brandCol : undefined}>
          <BrandMark size={logo} />
          <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.6}>
            {PROJECT.name}
          </Text>
          <Text style={styles.lead} maxFontSizeMultiplier={1.8}>
            {PROJECT.tagline}
          </Text>
        </View>
        <View style={[styles.actions, split && styles.actionsSplit]}>
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
          <Text style={styles.footer} maxFontSizeMultiplier={1.5}>
            {PROJECT.schoolShort}
          </Text>
        </View>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    width: '100%',
    justifyContent: 'center',
    paddingVertical: spacing.xl,
    gap: 4,
  },
  landscape: {
    paddingVertical: spacing.md,
  },
  split: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 32,
  },
  brandCol: {
    flex: 1,
    alignItems: 'center',
  },
  actionsSplit: {
    flex: 1,
  },
  title: { ...type.title, textAlign: 'center', marginTop: spacing.md },
  lead: {
    ...type.body,
    textAlign: 'center',
    color: colors.muted,
    marginTop: 12,
    marginBottom: 28,
  },
  actions: { width: '100%', maxWidth: 480, alignSelf: 'center' },
  gap: { height: 16 },
  gapWide: { height: 24 },
  footer: { marginTop: 28, textAlign: 'center', color: colors.muted, fontSize: 15, lineHeight: 22 },
});
