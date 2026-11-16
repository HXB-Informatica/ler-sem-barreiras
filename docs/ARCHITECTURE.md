# Arquitetura

O Ler sem Barreiras é uma aplicação Android nativa, sem servidor. Toda a persistência, gravação, reconhecimento e leitura ocorrem no dispositivo.

## Módulos

| Módulo | Responsabilidade |
| --- | --- |
| `app` | Arranque, navegação, tema e ecrãs institucionais. |
| `core` | Modelos de domínio, tokenização e formato exportável JSON. |
| `alignment` | Alinhamento entre texto original e palavras reconhecidas. |
| `audio` | Gravação PCM, importação, conversão WAV 16 kHz e reprodução Media3. |
| `speech` | Interface do motor de fala e implementação JNI do whisper.cpp. |
| `data` | Room, ficheiros privados e pacote `content.json`. |
| `feature-teacher` | Criação, sincronização e correção manual. |
| `feature-reader` | Experiência do aluno e destaque sincronizado. |

Esta divisão segue o pedido original e evita um único módulo monolítico, sem introduzir injeção de dependências pesada. O `AppContainer` em `LerSemBarreirasApp` cria os serviços.

## Fluxo de sincronização

```
texto original + áudio
        ↓
conversão interna para PCM 16 kHz mono
        ↓
whisper.cpp (JNI, CPU, língua `pt`)
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

`SpeechRecognitionEngine` é a única API usada pela interface. A implementação atual é `NativeWhisperEngine`. Substituir o motor não exige reescrever o Modo Professor nem o leitor.

## Persistência e formato exportável

Cada conteúdo tem um identificador UUID e um diretório `contents/<id>/` no armazenamento privado:

- `audio.wav` — narração normalizada para reprodução e para o motor;
- `content.json` — pacote independente do Android (`format: lersembarreiras-content`).

A base Room guarda o mesmo estado para consultas rápidas. A reabertura de um conteúdo **não** volta a executar o motor.

## Permissões

- `RECORD_AUDIO` — gravação no Modo Professor.
- `WAKE_LOCK` — manter a CPU acordada durante a sincronização longa.
- **Sem** `INTERNET`.

A importação usa o seletor de documentos do Android (`ACTION_OPEN_DOCUMENT`).

## Interface

Jetpack Compose e Material 3. Textos desta fase em português europeu (`pt-PT`). Os recursos de *string* estão centralizados o suficiente para futura internacionalização, embora a v0.1 não exponha seleção de idioma.
