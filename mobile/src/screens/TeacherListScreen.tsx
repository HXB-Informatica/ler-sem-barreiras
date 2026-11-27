import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { LsbException } from '../core/lsbErrors';
import { isReadyForStudent, type ReadingContent } from '../core/models';
import { pickFile, unzipWhitelisted, warmupSpeech } from '../native/bridge';
import type { ContentRepository } from '../storage/contentRepository';
import { colors, type } from '../theme';
import { AppHeader } from '../ui/AppHeader';
import { Button } from '../ui/Button';
import { ContentCard } from '../ui/ContentCard';
import { EmptyState } from '../ui/EmptyState';
import { ScreenShell } from '../ui/ScreenShell';
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

  useEffect(() => {
    warmupSpeech().catch(() => undefined);
  }, []);

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
    <ScreenShell>
      <AppHeader title="Modo Professor" onBack={onBack} onAbout={onAbout} />
      <Button label="Criar conteúdo" onPress={onCreate} accessibilityLabel="Criar um conteúdo novo" />
      <View style={styles.importWrap}>
        <Button
          label="Importar exercício .lsb"
          onPress={importLsb}
          disabled={busy}
          variant="ghost"
          accessibilityLabel="Importar exercício .lsb"
        />
      </View>
      {contents.length === 0 ? (
        <EmptyState
          title="Ainda não existem conteúdos neste tablet."
          message="Crie um texto, grave a narração e sincronize. Tudo fica neste tablet, sem internet."
        />
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          {contents.map(item => {
            const ready = isReadyForStudent(item);
            return (
              <ContentCard
                key={item.id}
                title={item.title}
                subtitle={`${item.audioRelativePath ? `Narração ${formatDuration(item.durationMs)}` : 'Sem narração'} · ${formatDate(item.updatedAtEpochMs)}`}
                accessibilityLabel={`Abrir ${item.title}. ${ready ? 'Pronto para leitura' : 'Em preparação'}`}
                onPress={() => onOpen(item.id)}
                accent={ready ? colors.success : colors.teacherAccent}
                badge={
                  <View style={styles.badges}>
                    <StatusChip status={item.syncStatus} />
                    <Text style={styles.ready}>{ready ? 'Pronto para os alunos' : 'Em preparação'}</Text>
                  </View>
                }
                footer={
                  <Button
                    label="Eliminar"
                    variant="danger"
                    onPress={() => remove(item)}
                    accessibilityLabel={`Eliminar ${item.title}`}
                  />
                }
              />
            );
          })}
        </ScrollView>
      )}
    </ScreenShell>
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
  importWrap: { marginTop: 10, marginBottom: 12 },
  list: { paddingBottom: 40, gap: 12 },
  badges: { gap: 6 },
  ready: { ...type.meta, fontWeight: '700', color: colors.text },
});
