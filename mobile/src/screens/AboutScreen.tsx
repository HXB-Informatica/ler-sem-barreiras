import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Button } from '../ui/Button';
import { colors } from '../theme';

type Props = { onBack: () => void };

export function AboutScreen({ onBack }: Props) {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Button label="Voltar" onPress={onBack} variant="secondary" />
      </View>
      <ScrollView contentContainerStyle={styles.body}>
        <Text style={styles.title}>Sobre</Text>
        <Text style={styles.version}>Versão 0.2.0 (React Native)</Text>
        <Text style={styles.p}>
          Iniciativa educativa do Agrupamento de Escolas Gil Vicente, Portugal, destinada a apoiar
          crianças com dificuldades de leitura, comunicação e aprendizagem.
        </Text>
        <Text style={styles.p}>
          A HXB Informática e Tecnologia, Unipessoal Lda. participa como parceiro tecnológico,
          disponibilizando o desenvolvimento de forma voluntária e sem custos. O projeto pertence à
          iniciativa da escola. A HXB não reivindica titularidade exclusiva.
        </Text>
        <Text style={styles.p}>
          A aplicação é gratuita, de código aberto (GNU GPL v3.0) e funciona integralmente offline.
          Não existem contas, publicidade, analytics nem envio de dados de alunos para o exterior.
        </Text>
        <Text style={styles.p}>
          A sincronização automática corre no próprio tablet com whisper.cpp (MIT) e o modelo
          Whisper tiny. Não envia áudio para a Internet.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: { padding: 16 },
  body: { paddingHorizontal: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '700', color: colors.text },
  version: { marginTop: 8, marginBottom: 16, color: colors.muted },
  p: { fontSize: 17, lineHeight: 26, color: colors.text, marginBottom: 12 },
});
