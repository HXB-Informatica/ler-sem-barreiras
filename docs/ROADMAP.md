# Roteiro

## v0.2.0 — reescrita React Native (versão actual)

Decisão aceite em [adr/0001-migrar-para-react-native.md](adr/0001-migrar-para-react-native.md). Notas: [releases/v0.2.0.md](releases/v0.2.0.md).

A app móvel Kotlin 0.1.x não está a funcionar correctamente; o desenvolvimento activo passa para React Native.

- Scaffold em `mobile/` (React Native 0.88): ecrãs, persistência, `.lsb`, alinhador, áudio PCM
- Sincronização no tablet com o EBranch-w2vBERT2-EP (cabeça CTC, ONNX int8) dentro do APK
- Sem `INTERNET` na release, sem nuvem, mesma GPL-3.0-only
- Validação obrigatória no HOTWAV TAB R9 Plus em modo avião antes de pre-release instalável

## v0.1.x — linha Kotlin (congelada)

Última publicação instalável: **v0.1.1-beta** (`versionName` 0.1.1, `versionCode` 2).

- Fundação do repositório público e da licença GPL-3.0-only
- App Android Compose, Studio Windows, alinhador, laboratórios 7–9
- Correção da gravação na 0.1.1
- **Não** continua como base da app móvel. O código serve de referência de formatos e do Studio.

## v0.2.x — qualidade no equipamento de referência (depois da paridade RN)

- *Benchmarks* reais no HOTWAV TAB R9 Plus
- Medição do EBranch-w2vBERT2-EP no dispositivo de referência
- Serviço em primeiro plano para sincronizações longas
- Exportação/importação de pacotes entre tablets da escola, sem nuvem

## v0.3 — acessibilidade e conteúdos

- Mais opções de letra, espaçamento e temas
- Apoio de leitor de ecrã revisto com utilizadores
- Guias para professores
- Biblioteca de exemplos sintéticos autorizados

## Fora de âmbito (propositadamente)

- Aplicação web ou PWA (será um projeto independente)
- Contas, nuvem ou servidores da HXB
- Modelos pagos ou APIs remotas de voz
- Publicação numa loja com chave de produção enquanto a gestão de chaves não estiver definida
- Recolha de dados de aprendizagem dos alunos
- Reescrita do Studio Windows na v0.2.0
