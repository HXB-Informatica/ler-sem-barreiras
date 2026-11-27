import { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { ReadingContent } from '../core/models';
import type { ContentRepository } from '../storage/contentRepository';
import { colors, radii, type } from '../theme';
import { ScreenHeader } from '../ui/ScreenHeader';

type Props = {
  repository: ContentRepository;
  onBack: () => void;
  onOpen: (id: string) => void;
  onAbout: () => void;
};

export function ReaderListScreen({ repository, onBack, onOpen, onAbout }: Props) {
  const [contents, setContents] = useState<ReadingContent[]>([]);

  const reload = useCallback(async () => {
    setContents(await repository.listReadyForStudent());
  }, [repository]);

  useEffect(() => {
    reload().catch(error => Alert.alert('Erro', error instanceof Error ? error.message : 'Falha ao carregar.'));
  }, [reload]);

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Modo Aluno" onBack={onBack} onAbout={onAbout} />
      {contents.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>Ainda não há textos prontos a ler.</Text>
          <Text style={styles.lead}>
            Peça a um professor para criar um conteúdo e sincronizar a narração neste tablet, ou
            importar um exercício .lsb.
          </Text>
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
              <Text style={styles.cardTitle}>{item.title}</Text>
              <Text style={styles.meta}>{item.words.length} palavras</Text>
            </Pressable>
          ))}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  empty: { flex: 1, justifyContent: 'center', padding: 24, gap: 12 },
  emptyTitle: { ...type.heading, textAlign: 'center' },
  lead: { ...type.body, textAlign: 'center', color: colors.muted },
  list: { padding: 16, gap: 12 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 22,
    borderLeftWidth: 6,
    borderLeftColor: colors.studentAccent,
  },
  cardTitle: { fontSize: 22, fontWeight: '700', color: colors.text },
  meta: { marginTop: 6, color: colors.muted, fontSize: 16 },
});
