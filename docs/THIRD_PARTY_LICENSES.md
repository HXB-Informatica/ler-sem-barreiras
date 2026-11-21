# Licenças de terceiros

O código próprio do Ler sem Barreiras está sob **GNU GPL v3.0 apenas** (`GPL-3.0-only`). Componentes de terceiros mantêm as suas licenças. A GPL v3 é compatível com a MIT e Apache 2.0 usadas abaixo; os avisos originais devem ser preservados.

## EBranch-w2vBERT2-EP

- Artigo: Carvalho et al., *CAMÕES: A Comprehensive Automatic Speech Recognition Benchmark for European Portuguese*, IEEE ASRU 2025. https://arxiv.org/pdf/2508.19721
- Ponto de controlo: https://huggingface.co/inesc-id/EBranch-w2vBERT2-EP
- Grafo empacotado na app móvel: export ONNX int8 da cabeça CTC, https://huggingface.co/OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-ctc-onnx
- Export ONNX com descodificador de atenção (não empacotado): https://huggingface.co/OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-aed-onnx
- Licença dos pesos e dos grafos: CC-BY-4.0. A atribuição é ao INESC-ID e ao projeto CAMÕES.
- Runtime no tablet: ONNX Runtime para Android (`com.microsoft.onnxruntime:onnxruntime-android`), licença MIT

Os pesos **não** são versionados neste repositório. A compilação da app móvel descarrega-os e coloca-os no APK.

## whisper.cpp

- Projeto: https://github.com/ggml-org/whisper.cpp
- Versão integrada na compilação: v1.9.5
- Licença: MIT
- Copyright: Georgi Gerganov e colaboradores

O aviso MIT do whisper.cpp aplica-se ao código nativo do Studio Windows e da linha Kotlin 0.1. A app móvel 0.2 não o embute. Não remova o aviso das distribuições que ainda incluem esse código.

## Modelos Whisper GGML

- Origem dos pesos: OpenAI Whisper (https://github.com/openai/whisper)
- Conversão GGML: repositório whisper.cpp / Hugging Face `ggerganov/whisper.cpp`
- Modelo por omissão: `ggml-base.bin` (multilingue)
- Licença: MIT

A redistribuição no Studio Windows e no APK Kotlin 0.1 é feita ao abrigo dessa licença. Os modelos **não** são versionados neste repositório Git. A app móvel 0.2 não redistribui pesos Whisper.

## Android / AndroidX / Kotlin / Compose / Media3 / Room

Distribuídos pela Google e JetBrains sob as licenças Apache 2.0 aplicáveis a cada artefacto Maven. Consulte os POM das dependências para a lista exaustiva. Nenhuma destas bibliotecas envia dados para o exterior na configuração deste projeto.

## Mozilla Common Voice (laboratório, não redistribuído)

- Origem: [commonvoice.mozilla.org](https://commonvoice.mozilla.org/) / corpus `pt`
- Licença das gravações e frases: **CC-0**
- Este repositório **não** inclui os áudios. O laboratório descarrega ou lê um corpus local para `test-lab/work/` (gitignore)
- Não se tentam identificar locutores

## Montreal Forced Aligner (laboratório, opcional)

- [Montreal Forced Aligner](https://montreal-forced-aligner.readthedocs.io/): MIT
- Modelos acústicos de português da MFA: verificar a licença do modelo no momento da instalação
- **Não** entra na aplicação dos alunos. Só é invocado se `mfa` existir no `PATH`

## Piper TTS (laboratório, opcional)

- Motor [Piper](https://github.com/rhasspy/piper): MIT
- Voz europeia `pt_PT-tugão-medium` em [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices): MIT no repositório de vozes; gravações da voz CC0; o modelo foi afinado a partir de Hi-Fi TTS (CC BY 4.0), o que implica atribuição se essa voz for redistribuída
- **Não** é incluída na aplicação dos alunos. O laboratório usa PCM sintético por omissão.

## Contributor Covenant

O código de conduta adapta o Contributor Covenant 2.1, disponível para utilização nos termos do próprio projeto Contributor Covenant.

## Nota sobre copyleft

Qualquer combinação distribuída da aplicação (APK, código nativo e modelo) deve continuar a oferecer o código-fonte correspondente nos termos da GPL v3.0, incluindo os *scripts* de compilação que obtêm o modelo EBranch-w2vBERT2-EP e, nas distribuições do Studio e da linha 0.1, o whisper.cpp.
