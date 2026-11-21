import { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LsbException } from '../core/lsbErrors';
import { syncLabel } from '../core/lsb';
import type { ReadingContent } from '../core/models';
import { pickFile, unzipWhitelisted } from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { colors } from '../theme';
import { Button } from '../ui/Button';

type Props = {
  repository: ContentRepository;
  onBack: () => void;
  onCreate: () => void;
  onOpen: (id: string) => void;
};

export function TeacherListScreen({ repository, onBack, onCreate, onOpen }: Props) {
  const [contents, setContents] = useState<ReadingContent[]>([]);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    setContents(await repository.list());
  }, [repository]);

  useEffect(() => {
    reload().catch(error => Alert.alert('Erro', messageOf(error)));
  }, [reload]);

  const importLsb = async () => {
    try {
      setBusy(true);
      const picked = await pickFile('*/*');
      if (!picked) {
        return;
      }
      const files = await unzipWhitelisted(picked);
      const result = await repository.importLsbBase64Map(files);
      await reload();
      if (result.duplicate) {
        Alert.alert('Importação', 'Este exercício já existia neste tablet e não foi duplicado.');
      } else if (result.idRemapped) {
        Alert.alert('Importação', 'O identificador já estava em uso. O exercício foi guardado com um identificador novo.');
      } else {
        Alert.alert('Importação', 'Exercício importado e guardado neste tablet.');
      }
    } catch (error) {
      Alert.alert('Importação', messageOf(error));
    } finally {
      setBusy(false);
    }
  };

  const remove = (item: ReadingContent) => {
    Alert.alert('Eliminar', `Eliminar «${item.title}» deste tablet?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: () => {
          repository
            .delete(item.id)
            .then(reload)
            .catch(error => Alert.alert('Erro', messageOf(error)));
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Button label="Voltar" onPress={onBack} variant="secondary" />
        <Text style={styles.title}>Modo Professor</Text>
        <Button label="Importar .lsb" onPress={importLsb} disabled={busy} accessibilityLabel="Importar exercício .lsb" />
      </View>
      {contents.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>Ainda não existem conteúdos neste tablet.</Text>
          <Text style={styles.lead}>
            Crie um texto, grave a narração e sincronize automaticamente, tudo sem internet. Também
            pode importar um exercício .lsb do Studio.
          </Text>
          <Button label="Criar conteúdo" onPress={onCreate} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          {contents.map(item => (
            <Pressable
              key={item.id}
              onPress={() => onOpen(item.id)}
              accessibilityRole="button"
              accessibilityLabel={`Abrir ${item.title}`}
              style={styles.card}>
              <View style={styles.cardBody}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                <Text style={styles.meta}>{syncLabel(item.syncStatus)}</Text>
                <Text style={styles.meta}>{formatDate(item.updatedAtEpochMs)}</Text>
              </View>
              <Button label="Eliminar" variant="danger" onPress={() => remove(item)} accessibilityLabel={`Eliminar ${item.title}`} />
            </Pressable>
          ))}
          <Button label="Criar conteúdo" onPress={onCreate} accessibilityLabel="Criar conteúdo" />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function formatDate(epochMs: number): string {
  return new Date(epochMs).toLocaleString('pt-PT');
}

function messageOf(error: unknown): string {
  if (error instanceof LsbException) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Ocorreu um erro inesperado.';
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { padding: 16, gap: 12 },
  title: { fontSize: 24, fontWeight: '700', color: colors.text },
  empty: { flex: 1, justifyContent: 'center', padding: 24, gap: 16 },
  emptyTitle: { fontSize: 22, fontWeight: '700', color: colors.text, textAlign: 'center' },
  lead: { fontSize: 16, color: colors.muted, textAlign: 'center' },
  list: { padding: 16, gap: 12, paddingBottom: 40 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 16,
    gap: 12,
  },
  cardBody: { gap: 4 },
  cardTitle: { fontSize: 20, fontWeight: '700', color: colors.text },
  meta: { color: colors.muted },
});
