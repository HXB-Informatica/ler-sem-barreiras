# ADR 0001 — Migrar a aplicação de Kotlin/Android nativo para React Native

**Estado:** aceite  
**Data:** 18 de novembro de 2026  
**Versão a que se aplica:** 0.2.0  
**Decisores:** Agrupamento de Escolas Gil Vicente (responsável do projeto); HXB Informática e Tecnologia, Unipessoal Lda. (parceiro tecnológico)

## Contexto

A linha 0.1.x (última publicação: **v0.1.1-beta**) é uma aplicação Android nativa em Kotlin, Jetpack Compose e módulos Gradle (`app`, `feature-teacher`, `feature-reader`, `speech`, `audio`, `data`, `shared`, `studio`). A sincronização usa whisper.cpp por JNI e um alinhador em Kotlin.

Essa linha **não está a funcionar correctamente** para o uso previsto na escola:

- A sincronização e o ciclo completo (criar, gravar, alinhar, ler) **não foram validados** no tablet de referência HOTWAV TAB R9 Plus.
- A cadeia nativa (NDK, CMake, JNI, whisper.cpp, duas vias de invocação Android vs Studio Windows) é frágil: divergências de parâmetros (`-ml`) já produziram timestamps sobrepostos e cobertura insuficiente em voz real pt-PT.
- A correção de gravação na v0.1.1 (ficheiro copiado para o próprio caminho) mostra que o fluxo de áudio nativo ainda falhava em casos básicos.
- Compilar e depurar exige JDK 17, Android SDK, NDK 27 e CMake. Isso dificulta contribuições de professores e programadores que não trabalham no ecossistema Android nativo.
- O produto pedagógico (Modo Professor, Modo Aluno, destaque de palavras, `.lsb`) está definido; a implementação nativa não chegou a um estado fiável no equipamento escolar.

## Decisão

A **versão 0.2.0** inicia a reescrita da aplicação móvel em **React Native**. A linha Kotlin 0.1.x deixa de ser a base de desenvolvimento activo. O código 0.1.x permanece no repositório como referência histórica e especificação viva (formatos, alinhamento, princípios offline), até a paridade funcional da 0.2.

Não se altera o propósito do produto: aplicação educativa, gratuita, **integralmente offline**, sem contas, sem telemetria e sem permissão `INTERNET` na app instalada.

## Consequências

### O que se mantém

- GPL-3.0-only, escola como responsável, HXB como parceiro tecnológico.
- Modo Professor e Modo Aluno descritos no README da v0.1.
- Formato de conteúdo (`content.json`) e pacote [`.lsb`](../lsb-format.md).
- Motor de fala **local**. A app móvel 0.2 usa o EBranch-w2vBERT2-EP (cabeça CTC em ONNX). A UI em JavaScript/TypeScript não implica APIs de voz na nuvem.
- Sem recolha de dados de alunos.

### O que muda

- Interface e navegação: React Native (TypeScript), em vez de Jetpack Compose.
- Áudio, ficheiros e o motor de fala: módulos nativos (Android; iOS só se for decidido mais tarde e sem alargar o âmbito desta versão). O motor ligado é o EBranch-w2vBERT2-EP, não o whisper.cpp.
- Lógica de alinhamento: portada a partir de `:shared` / `:alignment`, com os mesmos testes de laboratório sempre que possível.
- O Studio Windows (`:studio`, Compose Desktop) **não** é reescrito nesta decisão. Continua a ser a ferramenta de mesa da linha 0.1 até haver um plano próprio.
- Distribuição: a 0.2.0 **não** substitui de imediato o APK 0.1.1-beta nas Releases; a primeira build React Native será uma pre-release quando existir um ciclo mínimo (criar texto, gravar ou importar áudio, sincronizar, ler).

### Riscos aceites

- Esforço de reescrita e período sem app pedagógica estável.
- Necessidade de um módulo nativo bem testado para o motor de fala; o React Native não resolve sozinho o reconhecimento. O motor ligado é o EBranch-w2vBERT2-EP.
- Metro/npm na máquina de compilação (como o Gradle hoje); a app instalada continua sem rede.
- Tamanho do APK e desempenho no Unisoc T7280 têm de ser medidos no R9 Plus **antes** de se considerar a 0.2 pronta para a sala de aula.

### Fora de âmbito da 0.2.0

- Expo na nuvem, OTA updates, analytics, contas ou backends.
- Publicação em lojas com chave de produção.
- Aplicação web/PWA (continua projeto independente, ver [ROADMAP.md](../ROADMAP.md)).

## Alternativas rejeitadas

| Alternativa | Motivo da recusa |
| --- | --- |
| Continuar só em Kotlin e corrigir bugs | O estado 0.1.x não chegou a validação no tablet; a complexidade NDK/JNI e a falta de contribuidores Android nativos bloqueiam o ritmo da escola. |
| Kotlin Multiplatform / Compose Multiplatform para o telemóvel | Já existe Compose no Studio; não resolve a falha da app no tablet nem alarga o conjunto de contribuidores. |
| Flutter | Ecossistema Dart menos familiar para o parceiro tecnológico neste projeto; React Native cobre o mesmo tipo de UI híbrida com módulos nativos. |
| App só web | Viola o requisito offline no tablet sem servidor e foi explicitamente excluída do produto. |

## Referências

- [Notas da versão 0.2.0](../releases/v0.2.0.md)
- [Limitações da linha 0.1](../LIMITATIONS.md)
- [Diagnóstico Fase 9](../fase9-diagnostico.md)
- [Arquitetura](../ARCHITECTURE.md)
