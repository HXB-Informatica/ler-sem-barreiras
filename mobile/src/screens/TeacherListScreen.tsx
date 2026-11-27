import { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LsbException } from '../core/lsbErrors';
import type { ReadingContent } from '../core/models';
import { pickFile, unzipWhitelisted } from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { colors, radii, type } from '../theme';
import { Button } from '../ui/Button';
import { ScreenHeader } from '../ui/ScreenHeader';
import { StatusChip } from '../ui/StatusChip';

type Props = {
  repository: ContentRepository;
  onBack: () => void;
  onCreate: () => void;
  onOpen: (id: string) => void;
  onAbout: () => void;
};

export function TeacherListScreen({ repository, onBack, onCreate, onOpen, onAbout }: Props) {
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
      <ScreenHeader
        title="Modo Professor"
        onBack={onBack}
        onAbout={onAbout}
        extra={
          <Button
            label="Importar .lsb"
            onPress={importLsb}
            disabled={busy}
            accessibilityLabel="Importar exercício .lsb"
            variant="secondary"
          />
        }
      />
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
              accessibilityLabel={`Abrir ${item.title}, ${item.syncStatus}`}
              style={styles.card}>
              <View style={styles.cardBody}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                <StatusChip status={item.syncStatus} />
                <Text style={styles.meta}>
                  {item.audioRelativePath ? `Narração ${formatDuration(item.durationMs)}` : 'Sem narração'}
                </Text>
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

function formatDuration(durationMs: number): string {
  const total = Math.max(0, Math.round(durationMs / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
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
  empty: { flex: 1, justifyContent: 'center', padding: 24, gap: 16 },
  emptyTitle: { ...type.heading, textAlign: 'center' },
  lead: { ...type.body, textAlign: 'center', color: colors.muted },
  list: { padding: 16, gap: 12, paddingBottom: 40 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 16,
    gap: 12,
    borderLeftWidth: 6,
    borderLeftColor: colors.teacherAccent,
  },
  cardBody: { gap: 8 },
  cardTitle: { fontSize: 20, fontWeight: '700', color: colors.text },
  meta: { color: colors.muted, fontSize: 15 },
});
