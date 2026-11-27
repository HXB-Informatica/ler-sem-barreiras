import { Alert, Linking, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { approvedSupporters, installedVersion, PROJECT } from '../about/projectInfo';
import { colors, radii, spacing, type } from '../theme';
import { BrandMark } from '../ui/BrandMark';
import { Button } from '../ui/Button';

type Props = { onBack: () => void };

export function AboutScreen({ onBack }: Props) {
  const version = installedVersion();
  const supporters = approvedSupporters();

  const openGithub = async () => {
    try {
      await Linking.openURL(PROJECT.githubUrl);
    } catch {
      Alert.alert(
        'Repositório',
        `A página precisa de um navegador. Endereço: ${PROJECT.githubUrl}`,
      );
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Button label="Voltar" onPress={onBack} variant="secondary" />
      </View>
      <ScrollView contentContainerStyle={styles.body}>
        <BrandMark size={96} />
        <Text style={styles.title} accessibilityRole="header">
          Sobre o projeto
        </Text>
        <Text style={styles.version}>
          Versão {version.versionName} ({version.versionCode})
        </Text>
        <Section title="O que é">
          {PROJECT.name} é uma aplicação educativa, gratuita e integralmente offline. {PROJECT.mission}
        </Section>
        <Section title="Origem e iniciativa">
          {PROJECT.school} ({PROJECT.schoolShort}) é a entidade promotora e responsável pela coordenação
          pedagógica.
        </Section>
        <Section title="Para quem">
          {PROJECT.audience}
        </Section>
        <Section title="Como funciona">
          {PROJECT.howItWorks}
        </Section>
        <Section title="Desenvolvimento tecnológico">
          {PROJECT.techPartner} participa como {PROJECT.techRole}
        </Section>
        <View style={styles.card} accessibilityRole="summary">
          <Text style={styles.sectionTitle}>Apoiantes e financiadores</Text>
          {supporters.length === 0 ? (
            <Text style={styles.p}>
              Ainda não há entidades aprovadas pela escola para identificação nesta página. Nomes e
              logótipos só são apresentados depois dessa validação.
            </Text>
          ) : (
            supporters.map(item => (
              <View key={item.id} style={styles.supporter}>
                <Text style={styles.supporterName}>{item.name}</Text>
                <Text style={styles.meta}>{item.supportType}</Text>
              </View>
            ))
          )}
        </View>
        <Section title="Código aberto">
          A aplicação é gratuita para outras escolas e instituições. O código está publicado sob a
          licença {PROJECT.licenseName}. {PROJECT.copyrightNote}
        </Section>
        <Button
          label="Abrir o repositório GitHub"
          onPress={openGithub}
          variant="ghost"
          accessibilityLabel="Abrir o repositório GitHub no navegador, se existir ligação"
        />
        <Text style={styles.meta}>{PROJECT.githubUrl}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function Section({ title, children }: { title: string; children: string }) {
  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Text style={styles.p}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { padding: spacing.md },
  body: { paddingHorizontal: 20, paddingBottom: 40, gap: 12 },
  title: { ...type.title, marginTop: 8 },
  version: { ...type.meta, marginBottom: 8 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 16,
    gap: 8,
  },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: colors.primaryDeep },
  p: { ...type.body },
  meta: { ...type.meta, marginTop: 8 },
  supporter: { gap: 2 },
  supporterName: { fontSize: 17, fontWeight: '700', color: colors.text },
});
