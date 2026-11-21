# Auditoria técnica — Fase 1

**Projeto:** Ler sem Barreiras  
**Instituição:** Escola Gil Vicente (Agrupamento de Escolas Gil Vicente)  
**Parceiro tecnológico:** HXB (colaboração voluntária)  
**Data da auditoria:** 17 de novembro de 2026  
**Código auditado:** repositório local, versão da aplicação `0.1.1` (`versionCode` 2)  
**Âmbito:** análise integral sem alteração de código de produção, bibliotecas ou estrutura de módulos  
**Classificação:** **PASS WITH ISSUES**

Este documento confirma o que existe no código. Nada abaixo é assumido só a partir de `docs/ARCHITECTURE.md` ou `docs/OFFLINE_AI.md`; essas notas foram cruzadas com a implementação.

---

## 1. Arquitetura atual

A aplicação é **Android nativa, offline, sem servidor**. Não existe módulo Windows, plugin Kotlin Multiplatform, nem cliente de rede. O manifesto remove explicitamente `INTERNET`, `ACCESS_NETWORK_STATE` e `ACCESS_WIFI_STATE`. A persistência, a gravação, o reconhecimento e a leitura correm no dispositivo.

O arranque cria um contentor manual (`AppContainer` em `LerSemBarreirasApp`). Não há Hilt/Koin. Os ecrãs Compose obtêm ViewModels via factories.

```
app (Compose, navegação, tema)
 ├── feature-teacher  (criar / gravar / sincronizar / rever)
 ├── feature-reader   (leitura sincronizada)
 ├── speech           (JNI whisper.cpp)
 ├── audio            (gravação, importação, Media3)
 ├── data             (Room + ficheiros)
 ├── alignment        (JVM puro)
 └── core             (JVM puro: modelos, tokenização, JSON)
```

### 1.1 Configuração Gradle e Android

| Item | Valor confirmado |
| --- | --- |
| AGP | 8.7.3 |
| Gradle Wrapper | 8.11.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| JVM | 17 |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| ABI empacotadas | `arm64-v8a`, `x86_64` |
| Media3 | 1.5.1 |
| Room | 2.6.1 |
| applicationId | `pt.lersembarreiras.app` (sufixo `.debug` em debug) |
| Permissões | `RECORD_AUDIO`, `WAKE_LOCK` |
| Compose | Material 3, Navigation Compose |

Módulos Android (`com.android.library` / `application`): `app`, `audio`, `speech`, `data`, `feature-teacher`, `feature-reader`.  
Módulos JVM (`org.jetbrains.kotlin.jvm`): `core`, `alignment`.

Não há `androidTest/` com testes instrumentados, apesar de `app/build.gradle.kts` declarar Espresso/JUnit Android.

### 1.2 Interface

- Jetpack Compose em `app`, `feature-teacher` e `feature-reader`.
- Textos da UI em português europeu, sem seletor de idioma.
- Modo Professor: título, texto, gravar/importar, reproduzir, sincronizar, lista de palavras com correção manual (início/fim em milissegundos).
- Modo Aluno: três apresentações (`FULL_TEXT`, `SINGLE_WORD`, `PROGRESSIVE`), escala de letra e contraste elevado.
- Destaque da palavra atual: `ExoPlayer.currentPosition` amostrado ~32 ms, mapeado por `TimeValidator.currentWordIndex`.

---

## 2. Módulos existentes e responsabilidades

| Módulo | Tipo | Responsabilidade confirmada no código |
| --- | --- | --- |
| `:app` | Android application | `Application`/`AppContainer`, `MainActivity`, navegação, Home/About, tema |
| `:core` | Kotlin JVM | `ReadingContent`, `WordTiming`, `RecognizedWord`, `AlignmentResult`, `SyncStatus`, `ContentPackage`, `WordTokenizer`, `ContentPackageMapper` |
| `:alignment` | Kotlin JVM | `WordAligner` (Needleman–Wunsch) e `TimeValidator` |
| `:audio` | Android library | `AudioRecorder` (AudioRecord 16 kHz mono PCM16), `AudioImporter` (MediaExtractor/MediaCodec → WAV 16 kHz), `WavPcm`, `ContentPlayer` (Media3 ExoPlayer) |
| `:speech` | Android library + JNI | `SpeechRecognitionEngine`, `NativeWhisperEngine`, `WhisperJni`, `jni.cpp`, CMake, modelo em assets |
| `:data` | Android library | Room `contents`, ficheiros `filesDir/contents/<id>/` |
| `:feature-teacher` | Android library + Compose | Lista, edição, orquestração gravar → ASR → alinhar → guardar |
| `:feature-reader` | Android library + Compose | Lista de conteúdos sincronizados e leitura destacada |

Não existem módulos `:desktop`, `:windows`, `:shared` KMP, nem laboratório de testes autónomo.

---

## 3. Fluxo completo: gravação → reprodução sincronizada

```
Professor escreve título + texto
        ↓
Gravação (AudioRecord 16 kHz mono PCM16)  ou  importação (descodificação → WAV 16 kHz)
        ↓
Ficheiro privado contents/<uuid>/audio.wav
Room: audioRelativePath, durationMs, syncStatus = NONE
        ↓
Sincronizar (TeacherEditViewModel.synchronize)
        ↓
AudioImporter.loadPcm16k(audio.wav)
        ↓
NativeWhisperEngine.transcribe(pcm, language="pt", prompt=texto.take(400))
        ↓
JNI whisper_full (greedy, token_timestamps, split_on_word)
        ↓
JSON de palavras {text, startMs, endMs, probability, tokenTimestamps}
        ↓
WordAligner.align(texto original, recognized, durationMs)
        ↓
TimeValidator.validate (limites, ordem, duração mínima)
        ↓
Room + contents/<id>/content.json  (syncStatus COMPLETED | COMPLETED_WITH_GAPS | FAILED)
        ↓
Modo Aluno: ExoPlayer + TimeValidator.currentWordIndex(positionMs)
```

Pontos confirmados neste fluxo:

1. A reabertura **não** volta a correr o motor; lê Room/`content.json`.
2. O texto do professor **não** é a transcrição forçada: entra só como `initial_prompt` truncado a 400 caracteres.
3. Palavras sem correspondência fiável ficam com `startMs`/`endMs` nulos e `needsReview = true` (não são interpoladas).
4. O professor pode corrigir tempos (`manuallyAdjusted = true`, `needsReview = false`, `confidence = 1`).
5. O modo aluno lista conteúdos com `COMPLETED` ou `COMPLETED_WITH_GAPS`. Palavras sem tempo não destacam janela própria.

Cancelamento: `cancelRequested` + `WhisperJni.nativeCancel` → `abort_callback` no whisper.cpp.

---

## 4. Gravação e reprodução de áudio

### 4.1 Gravação (`AudioRecorder`)

- Fonte: `MediaRecorder.AudioSource.MIC`.
- Formato: 16 kHz, mono, PCM 16-bit — a taxa exigida pelo Whisper.
- Amostras acumuladas em memória (`List<ShortArray>`) até `stopToFile`.
- Escrita WAV via `WavPcm.writeWav`.
- Permissão pedida em runtime em `TeacherEditScreen`.
- Falha se `AudioRecord` não inicializar (dispositivos que recusem 16 kHz nativo).

### 4.2 Importação (`AudioImporter`)

- `ACTION_OPEN_DOCUMENT` com `audio/*`.
- PCM cru: lê amostras e converte para mono se `CHANNEL_COUNT > 1`.
- Formatos comprimidos: `MediaCodec` descodifica; **não** converte canais para mono no caminho comprimido. Áudio estéreo importado pode ser tratado como um fluxo intercalado e reamostrado como se fosse mono, distorcendo duração e timestamps do Whisper.
- Reamostragem linear para 16 kHz em `WavPcm.resampleTo16k`.

### 4.3 Reprodução (`ContentPlayer`)

- Media3 `ExoPlayer`, `MediaItem.fromUri(Uri.fromFile)`.
- Posição, pausa, recomeço e `seekTo` (usado na correção «Ouvir» palavra).
- `playbackState()` emite `PlaybackSnapshot` a cada 32 ms.

Não há relógio paralelo ao leitor. Isso está implementado como documentado.

---

## 5. Motor de reconhecimento (whisper.cpp)

**Confirmado: usa whisper.cpp**, não API cloud, não SpeechRecognizer do Android, não Whisper Python.

| Parâmetro | Valor no código |
| --- | --- |
| Origem | GitHub `ggml-org/whisper.cpp` tag **v1.9.5** (`speech/build.gradle.kts`) |
| Integração | CMake `add_subdirectory` + biblioteca partilhada `lersembarreiras_whisper` |
| JNI | `speech/src/main/cpp/jni.cpp` ↔ `WhisperJni` |
| Modelo por omissão | `ggml-base.bin` (multilingue), copiado de assets para `filesDir/models/` |
| Modelo configurável na compilação | `-PwhisperModel=` (ex.: `ggml-tiny.bin`) |
| GPU | `use_gpu = false`, `flash_attn = false`; GGML CUDA/Metal/Vulkan desligados |
| Sampling | `WHISPER_SAMPLING_GREEDY` |
| Língua | `"pt"` (Whisper; **não** distingue pt-PT/pt-BR) |
| Tradução | `translate = false` |
| `no_context` | `true` |
| `token_timestamps` | `true` |
| `split_on_word` | `true` |
| `max_len` | 60 |
| `suppress_blank` | `true` |
| Prompt | `initial_prompt` = texto do conteúdo, máx. 400 caracteres |
| Threads no `whisper_full` | `hardware_concurrency` limitado a 1–6 (**não** usa o `nThreads` passado a `nativeInit`) |
| Unidades de tempo | `t0`/`t1` do whisper (centésimos de segundo) × 10 → milissegundos |

O modelo **não está no Git**. A tarefa `fetchWhisperModel` obtém-no de Hugging Face `ggerganov/whisper.cpp` **só na máquina de compilação**.

Agrupamento de palavras no JNI: concatena tokens até aparecer um token que **começa por espaço**. Tokens especiais (SOT/EOT/etc.) são ignorados. Se o BPE do modelo não inserir espaço à esquerda (comum em palavras curtas ou em fusões), várias palavras do texto saem como **um único** `RecognizedWord`.

Não existe:

- alinhamento forçado (`whisper_align` / DTW contra o texto do professor);
- VAD próprio;
- seleção de modelo em runtime;
- API HTTP.

A interface `SpeechRecognitionEngine` isola o motor. Só `NativeWhisperEngine` a implementa. Essa implementação depende de `android.content.Context`, assets e `org.json.JSONObject`.

---

## 6. Algoritmo que associa palavras reconhecidas ao texto

Classe: `alignment/.../WordAligner.kt`.

1. `WordTokenizer.tokenize` parte o texto **por espaços em branco**. A grafia original (incluindo vírgulas coladas) fica em `WordTiming.text`; a comparação usa forma NFD sem diacríticos, minúsculas `pt-PT`, só letras/números.
2. Cada `RecognizedWord.text` é normalizado da mesma forma.
3. Programação dinâmica estilo Needleman–Wunsch:
   - match = +4
   - mismatch = −2
   - gap = −1
4. Semelhança fuzzy (`similarity`):
   - igualdade → +4
   - Levenshtein 1 e palavra ≥ 3 caracteres → +3
   - Levenshtein 2 e palavra ≥ 6 caracteres → +1
   - prefixo partilhado se a mais curta ≥ 4 caracteres → +1
   - resto → −2
5. No traceback, um passo diagonal **só grava o par se `similarity > 0`**. Mesmo assim consome os dois índices.
6. Palavra original sem par: `startMs`/`endMs` nulos, `confidence = 0`, `needsReview = true`.
7. Palavra emparelhada: tempos do reconhecido; `confidence = probability × (sim/matchScore)`; `needsReview` se confiança < 0,35 ou semelhança < match perfeito.
8. `TimeValidator` limita ao comprimento do áudio, marca ordem não monotónica (`start + 8 < previousEnd`), intervalos < 20 ms em palavras com mais de 2 caracteres, e `end == start`.

**Não há tabela de numerais** (`um`↔`1`, `dois`↔`2`, `três`↔`3`).  
**Não há divisão posterior** de um token reconhecido em várias palavras do texto.

Testes unitários cobrem pontuação, maiúsculas, omissões, hesitações, pausas longas e um erro ASR (`rapido`/`rapidoo`). **Não** cobrem a frase «Olá um dois três», numerais árabes, nem fusão de tokens.

---

## 7. Modelo de dados

### 7.1 Palavra alinhada — `WordTiming`

| Campo | Significado |
| --- | --- |
| `text` | Substring original (com pontuação colada) |
| `startChar` / `endChar` | Offsets no texto do professor |
| `startMs` / `endMs` | Janela temporal, ou `null` se não alinhada |
| `confidence` | 0–1, produto da probabilidade Whisper e da semelhança |
| `manuallyAdjusted` | Correção do professor |
| `needsReview` | Falha de alinhamento, baixa confiança, ou intervalo inválido |
| `isTimed` | ambos os tempos presentes e `end >= start` |

### 7.2 Reconhecido — `RecognizedWord`

`text`, `startMs`, `endMs`, `probability` (média dos `token.p` no JNI).

### 7.3 Conteúdo — `ReadingContent`

`id` UUID, `title`, `text`, `audioRelativePath`, `durationMs`, `words`, `syncStatus`, timestamps de criação/atualização.  
`reviewCount` = palavras com `needsReview` ou sem tempo.

### 7.4 Estados de sincronização — `SyncStatus`

`NONE`, `IN_PROGRESS`, `COMPLETED`, `COMPLETED_WITH_GAPS`, `FAILED`, `CANCELLED`.

`AlignmentResult.status`: zero palavras ou zero alinhadas → `FAILED`; alguma a rever → `COMPLETED_WITH_GAPS`; senão `COMPLETED`.

### 7.5 Pacote exportável — `ContentPackage`

`format = "lersembarreiras-content"`, `version = 1`. Serialização kotlinx.serialization JSON. O pacote é **independente do Android** no esquema, mas só é escrito pelo repositório Android.

Não há entidade de «sessão de aluno», telemetria, nem histórico de revisões para além dos flags em cada palavra.

---

## 8. Persistência

Por conteúdo:

```
<filesDir>/contents/<id>/audio.wav
<filesDir>/contents/<id>/content.json
```

Base Room `ler-sem-barreiras.db`, tabela `contents`:

- metadados em colunas;
- lista de `WordTiming` em `wordsJson`;
- `syncStatus` como `enum.name`.

Versão da base: 1, `exportSchema = false`, `fallbackToDestructiveMigration()`.

O modelo Whisper em runtime: `<filesDir>/models/ggml-base.bin` (cópia dos assets).

Não há sincronização entre dispositivos. O JSON está preparado para partilha futura por ficheiro (roteiro v0.2), mas a UI **não** exporta nem importa pacotes.

---

## 9. O que pode ir para Kotlin Multiplatform vs. o que é só Android

### Reutilizável com pouco trabalho (já JVM, sem Android)

- `core` — modelos, tokenizador (`java.text.Normalizer` / `Locale` exigem `expect/actual` ou kotlinx no futuro KMP).
- `alignment` — `WordAligner`, `TimeValidator`.
- `ContentPackageMapper` e o contrato `lersembarreiras-content`.
- A lógica pura de WAV em `WavPcm` (hoje vive no módulo Android `:audio`, mas o ficheiro não importa APIs Android).

### Reutilizável se se extrair uma API

- `SpeechRecognitionEngine` — a interface é Kotlin; a implementação JNI/Android não.
- O C++ `jni.cpp` está acoplado a JNI Android (`android/log.h`). O whisper.cpp em si é portável para Windows (DLL + bindings).

### Exclusivo Android (bloqueia partilha direta)

| Componente | Dependência |
| --- | --- |
| `AudioRecorder` | `AudioRecord` / `MediaRecorder` |
| `AudioImporter` | `MediaExtractor`, `MediaCodec`, `Uri` |
| `ContentPlayer` | Media3 ExoPlayer |
| `NativeWhisperEngine` | `Context`, assets, `JSONObject` |
| `WhisperJni` | `System.loadLibrary` + NDK `.so` |
| `ContentRepository` / Room / `filesDir` | AndroidX Room, `Context` |
| `feature-teacher` / `feature-reader` / `app` | Jetpack Compose Android, ViewModel, Navigation, permissões |
| CMake `target_link_libraries(... android log)` | NDK |

Não há Compose Multiplatform nem expect/actual. A UI do professor no Windows **não** pode reutilizar os ecrãs atuais sem uma camada nova.

---

## 10. Testes existentes e ambientes

### 10.1 Testes que existem (unitários)

| Ficheiro | O que cobre |
| --- | --- |
| `WordTokenizerTest` | Tokenização PT, offsets, Levenshtein |
| `WordAlignerTest` | Casos sintéticos de alinhamento |
| `TimeValidatorTest` | Clamp, ordem, `currentWordIndex` em dois intervalos contínuos |
| `WavPcmTest` | Round-trip WAV, reamostragem 48 kHz→16 kHz, PCM→float |
| `ContentPackageMapperTest` | JSON do pacote |
| `ContentRepositoryTest` | Room em memória + Robolectric, áudio não apagado ao copiar para o mesmo path |

Executados nesta auditoria (17/11/2026), **com sucesso**:

```text
.\gradlew.bat :core:test :alignment:test
.\gradlew.bat :audio:testDebugUnitTest :data:testDebugUnitTest
```

JDK 17 está disponível. O Android SDK local existe (`%LOCALAPPDATA%\Android\Sdk`). `ANDROID_HOME` não estava definido no ambiente da shell, mas o Gradle resolveu o SDK (via configuração local do projeto, não inspecionada nesta auditoria).

### 10.2 O que **não** existe, apesar de `docs/TESTING.md`

- Pasta `androidTest/` vazia / inexistente: **não há testes instrumentados automatizados**.
- Não há testes JNI/Whisper.
- Não há corpus de áudio sintético versionado.
- Não há laboratório Windows.
- Não há teste da frase «Olá um dois três».

A secção «Testes de integração» em `TESTING.md` descreve **caminhos da aplicação**, não suítes executáveis.

### 10.3 CI

`.github/workflows/android.yml`: Ubuntu, JDK 17, NDK/CMake, `./gradlew test`, `assembleDebug`, verifica `ggml-base.bin` e `liblersembarreiras_whisper.so` no APK, publica Release em tags `v*`.

### 10.4 Limitações desta auditoria (execução)

| Ambiente | Estado |
| --- | --- |
| Testes unitários JVM (`core`, `alignment`) | Executados, PASS |
| Testes unitários Android (`audio`, `data` / Robolectric) | Executados, PASS |
| `./gradlew test` completo (inclui `:speech`, `:app`, features) | Não corrido de novo na íntegra nesta sessão; os módulos acima já estavam UP-TO-DATE a PASS |
| Inferência whisper.cpp / JNI | **Não executada** — exigiria o modelo, a `.so` e uma chamada nativa; não há teste que o faça |
| Emulador / tablet (HOTWAV TAB R9 Plus) | **Nenhum dispositivo `adb` ligado** (`adb devices` vazio) |
| Aplicação Windows | **Não existe no repositório** |
| Áudio real «Olá um dois três» | **Não reproduzido**; o diagnóstico abaixo é por análise de código, não por medição no microfone |

A documentação existente já afirma que os ensaios no R9 Plus **ainda não foram feitos**. Esta auditoria confirma essa lacuna.

---

## 11. Diagnóstico: «Olá um dois três» mapeia só duas de quatro palavras

Não foi possível reproduzir a gravação real sem equipamento. O código **explica** o sintoma e torna-o o resultado esperado em vários caminhos, não um crash isolado.

Tokenização do texto: quatro tokens `ola`, `um`, `dois`, `tres`. O alinhador só marca palavra se existir `RecognizedWord` com semelhança **positiva**.

### Causa A — O motor não faz alinhamento forçado (principal, estrutural)

O Whisper transcreve livremente. O texto do professor é só *bias* (`initial_prompt`). Se `tiny` fundir, omitir ou numerar palavras, o alinhador **não recupera** os tempos a partir do texto.

### Causa B — Fusão de tokens no JNI (muito provável para 2/4)

As palavras JNI só se separam quando um token BPE **começa por espaço**. Com `tiny` e frases curtas é frequente obter, por exemplo:

- `["Olá", "um dois três"]`
- `["Olá um", "dois três"]`

Nesse caso o alinhador consegue emparelhar **duas** âncoras (ex.: `ola` exacto e `dois` por prefixo se a cadeia reconhecida começar por `dois` e tiver ≥ 4 caracteres) e deixa `um` e `três` sem tempo.

O teste unitário de omissão (`"o gato preto corre"` com três reconhecidas) documenta exactamente este comportamento: palavra sem par → `null` + `needsReview`.

### Causa C — Numerais árabes (muito provável em PT)

Whisper em português transcreve frequentemente «um dois três» como `1 2 3`.

`similarity("um","1")`, `similarity("dois","2")`, `similarity("tres","3")` caem no ramo `mismatchScore` (−2): palavras curtas **não** entram nas regras de Levenshtein (≥ 3 ou ≥ 6 caracteres). Mesmo com quatro tokens reconhecidos e timestamps válidos, **três palavras numerais falham o emparelhamento**. «Olá» pode casar, e uma quarta palavra só casa se o ASR a tiver escrito por extenso (daí «duas de quatro» se `dois` vier escrito e `um`/`três` vierem como dígitos, ou o inverso com fusões).

Não há teste que cubra este caso. É uma lacuna do alinhador, não só do modelo.

### Causa D — Qualidade temporal de `tiny` + `token_timestamps`

A própria documentação interna classifica timestamps de token como experimentais. Intervalos iguais, ordem invertida ou < 20 ms disparam `needsReview`. Palavras de 2 caracteres (`um`) **escapam** à regra `tooShort` (`text.length > 2`), o que é inconsistente mas não explica por si só a ausência de mapeamento.

### Causa E — Áudio muito curto / 16 kHz recusado

Gravações de um ou dois segundos, silêncio inicial, ou falha parcial do `AudioRecord` reduzem o que o `tiny` emite. Uma transcrição de duas palavras produz no máximo dois pares.

### Causa F — Destaque vs. mapeamento (efeito na UI do aluno)

Mesmo com quatro `WordTiming`, `currentWordIndex` **ignora** palavras sem tempo e, no intervalo *antes* do início da próxima palavra temporizada, devolve essa próxima palavra (`lastTimed` é atualizado antes do teste `positionMs < start`). O aluno pode perceber «só duas palavras a acender» mesmo que a lista do professor mostre quatro linhas, duas delas «Sem tempo fiável».

**Conclusão do diagnóstico:** o sintoma é coerente com ASR livre + `tiny` + agrupamento por espaço + ausência de mapa numeral + política de não inventar timestamps. Não é necessário um bug de persistência para o produzir. Sem corrida Whisper nesta máquina, **não** se fixa uma única causa A–E; A+B+C são as hipóteses com maior suporte no código.

---

## 12. Palavras não reconhecidas, repetidas e omitidas

| Situação | Comportamento confirmado |
| --- | --- |
| Omissão no áudio / ASR | Gap para cima na DP; palavra original sem tempos; `needsReview`; estado `COMPLETED_WITH_GAPS` se alguma outra alinhou |
| Extra / hesitação (`uh`) | Gap para a esquerda; incrementa `unusedRecognizedCount`; não cria palavra extra no texto |
| Repetidas no texto e no ASR, pela ordem | Emparelhamento sequencial (teste `"o gato o gato"`) |
| Repetida só no ASR | A extra conta como não usada |
| Erro ASR ligeiro | Levenshtein limitado; `rapido`/`rapidoo` alinha com revisão; erros em palavras de 2–3 letras quase não têm folga |
| Confiança baixa | `needsReview` se `< 0.35` mesmo com par |
| Revisão humana | Diálogo de ms; não há arrasto visual na forma de onda |
| Falha total do ASR | Todas as palavras a rever; `FAILED` |
| UI professor | Fundo `errorContainer` + «Requer revisão» / «Sem tempo fiável» |
| UI aluno | Palavras sem tempo não definem janela; o destaque salta entre as que têm tempo |

Não há interpolação linear entre vizinhos. Isso evita timestamps falsos, mas deixa buracos visíveis em frases curtas escolares.

Empate mismatch vs. dois gaps: `mismatchScore == 2 × gapScore` (−2). A tabela prefere a diagonal em empate (`diagonal >= up && diagonal >= left`) e **não** associa se `sim <= 0`, consumindo mesmo assim os dois índices. Isso pode «gastar» um reconhecido contra uma palavra incompatível (ex. `um` vs `1`) e prejudicar o resto da frase.

---

## 13. Componentes reutilizáveis (para Android + Windows)

Prioridade de extração, sem as implementar nesta fase:

1. **Domínio e pacote JSON** (`core`) — contrato estável entre apps.
2. **Alinhador e validador** (`alignment`) — idênticos em qualquer SO.
3. **WAV PCM 16 kHz** — extrair de `:audio`.
4. **Contrato `SpeechRecognitionEngine`** — nova implementação Windows (whisper.cpp nativo) com o mesmo JSON de palavras.
5. **Regras de revisão** (`needsReview`, `SyncStatus`) — partilhadas.

O laboratório de testes da Fase 2 deve depender só de (1)–(4) em JVM, sem emulador.

---

## 14. Dependências Android que dificultam a reutilização

1. Room + `Context.filesDir` no repositório.  
2. Media3 no leitor.  
3. `AudioRecord` / `MediaCodec` na captura.  
4. JNI e CMake Android no módulo `speech` (log, STL, ABI `.so`).  
5. Compose + Navigation + ViewModel Android nas features.  
6. `org.json.JSONObject` no parser Whisper (substitível por kotlinx.serialization, já usado em `core`).  
7. `minSdk`/NDK a obrigar o módulo `speech` a ser `com.android.library`.

Estas dependências são adequadas à app Android atual; tornam-se obstáculo se se copiar módulos inteiros para um `.exe`.

---

## 15. Proposta de migração incremental (Android + Windows)

**Não iniciada.** Ordem sugerida para não partir a app Android funcional:

1. **Laboratório JVM** (Fase 2 imediata): corpus WAV sintético + invocação do alinhador (e, se o SDK/NDK o permitir, whisper.cpp em processo de teste, não na UI). Congelar métricas da frase «Olá um dois três» e de omissões/repetições.
2. **Endurecer o alinhador** contra numerais PT, fusão de tokens e empates DIAG, **com testes a vermelho primeiro** — ainda sem KMP.
3. **Extrair `WavPcm` + contrato de motor** para um módulo JVM (ou KMP `commonJvm`) usado pelo Android atual via `api(project)`.
4. **Persistência em ficheiros `content.json` + WAV** como fonte de verdade; Room permanece cache Android.
5. **App Windows do professor** (Compose Multiplatform desktop *ou* UI nativa mínima) que: edita texto, grava/importa WAV, corre o mesmo motor local, permite rever timestamps, escreve o pacote `lersembarreiras-content`. Sem nuvem.
6. **Manter o APK Android** como leitor (e professor em tablet) a consumir o mesmo pacote.
7. Só depois: JNI comum / biblioteca nativa partilhada Windows (`x86_64`) e Android (`arm64-v8a`).

Restrições a preservar: GPL-3.0, offline, sem contas, sem APIs de voz pagas.

---

## 16. Riscos e recomendações

| Risco | Gravidade | Recomendação (Fase 2, não executar agora) |
| --- | --- | --- |
| `base` + timestamps de token experimentais em PT escolar | Alta | Medir no R9 Plus. O default passou a `base` sem esse ensaio; `tiny` continua disponível com `-PwhisperModel=` |
| Sem alinhamento forçado ao texto do professor | Alta | Avaliar DTW / whisper alinhado ao script; o objetivo do produto é cronometrar o texto, não transcrever |
| Numerais e palavras de 2–3 letras | Alta | Mapa `1`↔`um`… e relaxar `similarity` para tokens curtos, com testes |
| Fusão JNI por espaço inicial | Alta | Separar também por pontuação; opcionalmente partir reconhecidos longos pelas palavras do texto |
| Importação estéreo via MediaCodec sem downmix | Média | Downmix no mesmo sítio que `readRaw` |
| `AudioRecord` a 16 kHz nativo | Média | Gravar na taxa nativa e reamostrar com `WavPcm` |
| `nThreads` de `nativeInit` ignorado | Baixa | Passar `n_threads` coerente |
| `currentWordIndex` salta à palavra seguinte na pausa | Média | Devolver a última palavra cujo `endMs` já passou |
| Sem testes de motor nem dispositivo | Alta | Laboratório automatizado + ensaio no R9 Plus quando houver hardware |
| Room `fallbackToDestructiveMigration` | Média | Migrações explícitas antes de mudar o esquema |
| APK de debug / chave de debug | Já aceite na v0.1 | Fora desta fase |
| KMP prematuro | Média | Só após o laboratório e o contrato JSON estáveis |

---

## 17. Relatório técnico e veredito

### O que está sólido

- Arquitetura modular alinhada com o domínio (texto, áudio, fala, alinhamento, persistência, duas UIs).
- Offline real (sem permissão de Internet na app).
- whisper.cpp v1.9.5 por JNI, modelo `base` multilingue, língua `pt`, CPU only — confirmado no C++ e no Gradle.
- Domínio e alinhador já separados da UI, testáveis em JVM.
- Persistência duplicada (Room + JSON) adequada a um pacote partilhável.
- Correção manual e estados de revisão existem e são persistidos.
- Testes unitários existentes passam neste ambiente.

### Issues materiais

- Falhas de alinhamento em frases curtas são **plausíveis e em grande parte estruturais**, não apenas «bug de UI».
- Não há laboratório ASR, nem testes da frase reportada, nem dispositivo ligado para validar o R9 Plus.
- O alinhador não trata numerais nem fusão de tokens Whisper.
- Integração Windows / KMP inexistente (esperado nesta versão, mas é um fosso para o objetivo declarado).
- «Testes de integração» documentados não estão automatizados.
- Dois defeitos de áudio/destaque (estéreo importado; índice da palavra atual nas pausas) confirmados por leitura de código.

### Classificação

**PASS WITH ISSUES**

Não é FAIL: o repositório é compilável na prática (há APK de debug no tree de outputs), os módulos estão identificados, o motor é local e auditável, e os testes unitários correm.  
Não é PASS limpo: o problema pedagógico central (sincronizar todas as palavras de uma narração escolar curta) não está demonstrado, o código tem lacunas que o impedem, e falta o ambiente de teste que a Fase 2 precisa.

### Proposta concreta para a Fase 2 (não iniciar aqui)

1. Criar um **laboratório JVM** em `lab/` ou `:lab` (sem alterar o fluxo da app) com WAV sintéticos gerados (silêncio + tons ou TTS local se vier a ser adicionado **offline**; na ausência de TTS, PCM com timestamps conhecidos injetados **a montante** do alinhador e, quando possível, um binário whisper.cpp de teste).
2. Casos obrigatórios: «Olá um dois três»; omissões; repetições; hesitações; pontuação; numerais `1 2 3`; uma frase de 20–40 palavras.
3. Relatório de métricas: palavras alinhadas, atraso vs. referência, `needsReview`.
4. Corrigir o alinhador **guiado por esses testes** (numerais PT, não consumir DIAG com `sim <= 0`, eventual divisão de reconhecidos fundidos). Não substituir whisper.cpp nesta fase.
5. Corrigir downmix de importação e `currentWordIndex` com testes, sem redesenhar a UI.
6. Definir o contrato do pacote como API entre Android e a futura app Windows; ainda **não** obrigar KMP completo.
7. Quando houver tablet: um ensaio manual no R9 Plus, em modo avião, registado em `docs/TESTING.md` — fora do laboratório automatizado.

Critério de saída da Fase 2: a frase «Olá um dois três» alinha **quatro** palavras no laboratório com áudio sintético controlado e com, pelo menos, uma transcrição Whisper gravada (quando o motor nativo puder correr no ambiente de CI ou numa máquina de desenvolvimento com NDK). Só então avançar para a app Windows.

---

*Fim da Fase 1. Nenhuma alteração de código de aplicação foi feita; apenas este documento foi adicionado.*
