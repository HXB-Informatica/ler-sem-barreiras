# Fase 2 — Núcleo partilhado

**Data:** 17 de novembro de 2026  
**Referência:** [architecture-audit.md](architecture-audit.md)  
**Classificação: PASS WITH ISSUES**

## Decisão: Kotlin/JVM em vez de KMP completo

Não foi aplicado o plugin Kotlin Multiplatform (`androidTarget` + `native`).

Motivos, cruzados com a auditoria:

- A futura app Windows prevista (Compose Desktop / JVM) partilha o **mesmo bytecode JVM** que `:core` e `:alignment` já usavam.
- O tokenizador depende de `java.text.Normalizer` e `Locale("pt","PT")`, APIs JVM/Android. Colocá-las em `commonMain` exigiria `expect/actual` sem ganho para Android+Windows.
- Os módulos Android já consomem bibliotecas `kotlin("jvm")` sem `androidTarget()`. Introduzir KMP com alvo Android no mesmo grafo AGP 8.7.3 aumentaria o risco de regressão sem benefício imediato.
- O reconhecimento (JNI whisper.cpp) e o Media3 continuam específicos de plataforma, como na auditoria.

O módulo `:shared` é **Kotlin/JVM 17**, o mesmo contrato que um alvo `jvm()` de KMP. Uma conversão KMP posterior pode copiar `src/main` para `commonMain`/`jvmMain` sem mudar os pacotes.

## Arquitetura

```
:shared   modelos, tokenização, AlignmentEngine, validações, serializer, contratos
:core     fachada api(:shared) — pacotes inalterados para a UI Android
:alignment  fachada api(:shared) — testes unitários existentes mantêm-se
Android   adaptadores (FileAudioSource, ContentPlayer, ContentRepository)
```

Transcrição (`SpeechRecognitionEngine` / whisper.cpp) **não** entrou em `:shared`. O `TeacherEditViewModel` transcreve primeiro e só depois chama `AlignmentEngine.align(AlignmentRequest)`.

### Contratos (independentes de Android)

| Interface | Implementação Android |
| --- | --- |
| `AlignmentEngine` | `DefaultAlignmentEngine` (JVM, usada no `AppContainer`) |
| `AudioSource` | `FileAudioSource` (`AudioImporter` + ficheiro WAV) |
| `PlaybackController` | `ContentPlayer` (Media3) |
| `AlignmentRepository` | `ContentRepository` (Room + ficheiros) |
| `ProjectSerializer` | `ContentPackageMapper` |

### Modelo de palavra

Campos novos, todos opcionais na deserialização para não quebrar JSON v1:

- `id` estável (`w-<índice>-<startChar>-<endChar>`)
- `index`
- `state`: `AUTO` \| `REVIEW` \| `MANUAL` \| `VERIFIED` (ausente no JSON antigo → derivado de `needsReview` / `manuallyAdjusted`)
- `timestampOrigin`: `NONE` \| `ASR` \| `ALIGNED` \| `MANUAL` \| `IMPORTED`

`manuallyAdjusted` e `needsReview` continuam a ser gravados para leitores antigos.

Uma nova sincronização **não** substitui palavras `MANUAL` ou `VERIFIED` com o mesmo índice e o mesmo texto.

O formato `lersembarreiras-content` permanece **versão 1** (campos aditivos; `ignoreUnknownKeys` já existia).

## Ficheiros alterados / criados

**Criados**

- `shared/` (módulo Gradle)
- `shared/src/main/kotlin/pt/lersembarreiras/core/model/Models.kt` (movido e alargado)
- `shared/src/main/kotlin/pt/lersembarreiras/core/text/WordTokenizer.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/core/packageformat/ContentPackageMapper.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/alignment/WordAligner.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/alignment/TimeValidator.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/alignment/WordIdentity.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/alignment/DefaultAlignmentEngine.kt`
- `shared/src/main/kotlin/pt/lersembarreiras/shared/Contracts.kt`
- testes em `shared/src/test/...`
- `audio/.../FileAudioSource.kt`
- este documento

**Removidos (duplicados após a extração)**

- fontes `main` de `:core` e `:alignment` (os testes desses módulos mantêm-se)

**Adaptados**

- `settings.gradle.kts`, `core/build.gradle.kts`, `alignment/build.gradle.kts`
- `LerSemBarreirasApp`, `TeacherEditViewModel`, `TeacherScreens`
- `ContentPlayer`, `ContentRepository`, `ContentEntity`, `ReaderViewModel`
- testes de `ContentPackageMapper` e `ContentRepository`

A UI Compose não foi reescrita: o modo professor/aluno usa os mesmos ecrãs, agora alimentados pelo motor partilhado.

## Limitações

- Sem app Windows nesta fase.
- Sem JNI/Whisper no módulo partilhado; a transcrição continua Android.
- Sem KMP `commonMain`.
- Importação estéreo via MediaCodec e o salto de `currentWordIndex` nas pausas **não** foram corrigidos (fora do critério da Fase 2).
- Não há testes instrumentados nem dispositivo ligado; a compilação Android é o proxy de «o APK anterior continua a construir».

## Testes executados (17/11/2026)

Todos com sucesso:

```text
.\gradlew.bat :shared:test :core:test :alignment:test :audio:testDebugUnitTest :data:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Cobertura obrigatória da Fase 2:

| Critério | Onde |
| --- | --- |
| «Olá um dois três» → 4 objetos | `DefaultAlignmentEngineTest` |
| Sem correspondência → REVIEW, tempos nulos | idem |
| IDs e índices estáveis | idem |
| MANUAL sobrevive a nova sincronização | idem |
| MANUAL sobrevive a gravar/carregar | `ContentRepositoryTest.reloadPreservesManualWordIdentityAndTimes` |
| Migração JSON v1 | `LegacyMigrationTest` |
| Android compila | `:app:assembleDebug` |

Não executado: app no tablet/emulador (`adb devices` vazio na Fase 1; não repetido como ensaio funcional).

## Relatório e veredito

O núcleo partilhado está funcional, com contratos de plataforma, alinhamento que materializa todas as palavras do texto, preservação de correções MANUAL/VERIFIED, migração aditiva do JSON v1 e APK debug gerado.

**PASS WITH ISSUES** — issues: KMP completo não foi introduzido (Kotlin/JVM documentado como via correcta para Android + Windows JVM); funcionalidades anteriores não foram reexercidas num dispositivo; Whisper/JNI continua só no Android.

A Fase 3 não foi iniciada.
