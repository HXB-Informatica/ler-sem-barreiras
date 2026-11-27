import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { ReadingContent } from '../core/models';
import type { ContentRepository } from '../storage/contentRepository';
import { AppHeader } from '../ui/AppHeader';
import { BrandMark } from '../ui/BrandMark';
import { ContentCard } from '../ui/ContentCard';
import { EmptyState } from '../ui/EmptyState';
import { useLayout } from '../ui/layout';
import { ScreenShell } from '../ui/ScreenShell';

type Props = {
  repository: ContentRepository;
  onBack: () => void;
  onOpen: (id: string) => void;
  onAbout: () => void;
};

export function ReaderListScreen({ repository, onBack, onOpen, onAbout }: Props) {
  const [contents, setContents] = useState<ReadingContent[]>([]);
  const layout = useLayout();

  const reload = useCallback(async () => {
    setContents(await repository.listReadyForStudent());
  }, [repository]);

  useEffect(() => {
    reload().catch(error => Alert.alert('Erro', error instanceof Error ? error.message : 'Falha ao carregar.'));
  }, [reload]);

  return (
    <ScreenShell>
      <AppHeader title="Modo Aluno" onBack={onBack} onAbout={onAbout} />
      {contents.length === 0 ? (
        <EmptyState
          illustration={<BrandMark size={88} />}
          title="Ainda não há histórias disponíveis."
          message="Pede ajuda ao teu professor para começares a ler."
        />
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          <View style={layout.columns > 1 ? styles.grid : undefined}>
            {contents.map(item => (
              <View key={item.id} style={layout.columns > 1 ? styles.cell : undefined}>
                <ContentCard
                  title={item.title}
                  subtitle="Toca para ouvir e ler"
                  accessibilityLabel={`Abrir ${item.title}`}
                  onPress={() => onOpen(item.id)}
                />
              </View>
            ))}
          </View>
        </ScrollView>
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  list: { paddingBottom: 32, gap: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  cell: { flexGrow: 1, flexBasis: '47%' },
});
