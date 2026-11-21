# Arquitetura

O Ler sem Barreiras é uma aplicação **offline, sem servidor**. Não há envio de gravações para a Internet.

**A partir da v0.2.0** a app móvel está em [`mobile/`](../mobile) (**React Native**, TypeScript). A descrição dos módulos Gradle abaixo documenta a **linha 0.1.x (Kotlin)**, ainda presente como referência e Studio Windows. Decisão: [adr/0001-migrar-para-react-native.md](adr/0001-migrar-para-react-native.md).

Na 0.1, a app Android corre no tablet; o **Studio Windows** (`:studio`) prepara narrações com microfone USB.

## Alvo da v0.2 (React Native)

```
mobile/ (React Native 0.88, TypeScript)
  ecrãs professor / aluno
        ↓
  LsbFiles + LsbAudio + LsbSpeech (Android)
        ↓
  src/core alinhador (portado de :shared)
        ↓
  filesDir/lsb/contents/<id>/content.json + audio.wav
  pacote .lsb (formato inalterado)
```

`LsbSpeech` corre o EBranch-w2vBERT2-EP (cabeça CTC em ONNX) dentro do APK. A importação `.lsb` e a leitura sincronizada usam o alinhador e o relógio do leitor. O fluxo é: WAV 16 kHz, características log-mel w2v-BERT, CTC ganancioso, alinhador sobre o texto do professor. Detalhe em [OFFLINE_AI.md](OFFLINE_AI.md).

A UI deixa de ser Jetpack Compose. O contrato de dados e o motor local **não** passam a ser serviços na Internet.

## Módulos (linha 0.1, Kotlin)

| Módulo | Responsabilidade |
| --- | --- |
| `app` | Arranque, navegação, tema e ecrãs institucionais. |
| `shared` | Modelos, alinhamento, contratos e editor de intervalos (Kotlin/JVM). |
| `core` | Fachada `api(:shared)` para os módulos Android. |
| `alignment` | Fachada `api(:shared)` e testes de alinhamento. |
| `audio` | Gravação PCM, importação, forma de onda, conversão WAV 16 kHz e reprodução Media3. |
| `speech` | Interface do motor de fala e implementação JNI do whisper.cpp. |
| `data` | Room, ficheiros privados e pacote `content.json`. |
| `feature-teacher` | Criação, sincronização e correção manual. |
| `feature-reader` | Experiência do aluno e destaque sincronizado. |
| `studio` | App Windows (Compose Desktop): captura WAV, editor de sincronização, whisper-cli nativo x64. |
| `lab-cli` | CLI interno do alinhador para o laboratório Python. **Não** entra no APK. |
| `test-lab` | Python interno: sintéticos (Fase 7) e vozes reais / dois motores (Fase 8). **Não** entra no APK. |

Esta divisão segue o pedido original e evita um único módulo monolítico, sem introduzir injeção de dependências pesada. O `AppContainer` em `LerSemBarreirasApp` cria os serviços.

## Fluxo de sincronização (linha 0.1 e Studio)

```
texto original + áudio
        ↓
conversão interna para PCM 16 kHz mono
        ↓
whisper.cpp (JNI na linha 0.1, whisper-cli no Studio)
        ↓
palavras reconhecidas com timestamps
        ↓
alinhamento por programação dinâmica com o texto do professor
        ↓
WordTiming(text, startMs, endMs, confidence, manuallyAdjusted)
        ↓
Room + contents/<id>/audio.wav + contents/<id>/content.json
```

A reprodução no Modo Aluno consulta `ExoPlayer.currentPosition`. Não se usa um relógio paralelo que possa acumular desvio.

## Motor de IA isolado

Na linha 0.1, `SpeechRecognitionEngine` é a única API usada pela interface e a implementação é `NativeWhisperEngine`. Na app móvel 0.2 o contrato equivalente é `LsbSpeech`, com o EBranch-w2vBERT2-EP.

## Persistência e formato exportável

Cada conteúdo tem um identificador UUID e um diretório `contents/<id>/` no armazenamento privado:

- `audio.wav` — narração normalizada para reprodução e para o motor;
- `content.json` — pacote independente do Android (`format: lersembarreiras-content`).

A base Room guarda o mesmo estado para consultas rápidas. A reabertura de um conteúdo **não** volta a executar o motor.

## Permissões

- `RECORD_AUDIO` — gravação no Modo Professor.
- `WAKE_LOCK` — manter a CPU acordada durante a sincronização longa.
- **Sem** `INTERNET`.

A importação de áudio usa o seletor de documentos do Android (`ACTION_OPEN_DOCUMENT`). A transferência de exercícios entre o Studio e o tablet usa o pacote [`.lsb`](lsb-format.md) (ZIP versionado, checksums SHA-256), sem servidor.

## Interface

Jetpack Compose e Material 3. Textos desta fase em português europeu (`pt-PT`). Os recursos de *string* estão centralizados o suficiente para futura internacionalização, embora a v0.1 não exponha seleção de idioma.
