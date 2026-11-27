import { Alert, Linking, StyleSheet, Text, View } from 'react-native';
import { approvedSupporters, installedVersion, PROJECT } from '../about/projectInfo';
import { colors, radii, type } from '../theme';
import { AppHeader } from '../ui/AppHeader';
import { BrandMark } from '../ui/BrandMark';
import { Button } from '../ui/Button';
import { ScreenShell } from '../ui/ScreenShell';

type Props = { onBack: () => void };

export function AboutScreen({ onBack }: Props) {
  const version = installedVersion();
  const supporters = approvedSupporters();

  const openGithub = async () => {
    try {
      await Linking.openURL(PROJECT.githubUrl);
    } catch {
      Alert.alert('Repositório', `Endereço: ${PROJECT.githubUrl}`);
    }
  };

  return (
    <ScreenShell scroll>
      <AppHeader title="Sobre o projeto" onBack={onBack} />
      <View style={styles.body}>
        <BrandMark size={96} />
        <Text style={styles.version}>
          Versão {version.versionName} ({version.versionCode})
        </Text>
        <Card title="O que é">
          {PROJECT.name} é uma aplicação educativa, gratuita e integralmente offline. Permite acompanhar
          as palavras escritas através de narrações sincronizadas, em especial para alunos com
          dificuldades de leitura e comunicação.
        </Card>
        <Card title="Origem e iniciativa">
          {PROJECT.school} é a entidade promotora da iniciativa e responsável pela coordenação
          pedagógica.
        </Card>
        <Card title="Desenvolvimento tecnológico">
          {PROJECT.techPartner}. {PROJECT.techRole}
        </Card>
        <View style={styles.card} accessibilityRole="summary">
          <Text style={styles.sectionTitle}>Parceiros e apoiantes</Text>
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
        <Card title="Código aberto">
          A aplicação é gratuita e pode ser usada por outras escolas e instituições. O código-fonte está
          publicado sob a licença {PROJECT.licenseName}. Programadores externos podem contribuir.{' '}
          {PROJECT.copyrightNote}
        </Card>
        <Button
          label="Abrir o repositório GitHub"
          onPress={openGithub}
          variant="ghost"
          accessibilityLabel="Abrir o repositório GitHub no navegador, se existir ligação"
        />
        <Text style={styles.meta}>{PROJECT.githubUrl}</Text>
        <Text style={styles.meta}>Licença {PROJECT.licenseName}</Text>
      </View>
    </ScreenShell>
  );
}

function Card({ title, children }: { title: string; children: string }) {
  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Text style={styles.p}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  body: { gap: 12, paddingBottom: 24 },
  version: { ...type.meta, textAlign: 'center', marginBottom: 8 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 16,
    gap: 8,
  },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: colors.primaryDeep },
  p: { ...type.body },
  meta: { ...type.meta, marginTop: 4 },
  supporter: { gap: 2 },
  supporterName: { fontSize: 17, fontWeight: '700', color: colors.text },
});
