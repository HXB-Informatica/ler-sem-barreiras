# Licenças de terceiros

O código próprio do Ler sem Barreiras está sob **GNU GPL v3.0 apenas** (`GPL-3.0-only`). Componentes de terceiros mantêm as suas licenças. A GPL v3 é compatível com a MIT e Apache 2.0 usadas abaixo; os avisos originais devem ser preservados.

## whisper.cpp

- Projeto: https://github.com/ggml-org/whisper.cpp
- Versão integrada na compilação: v1.9.5
- Licença: MIT
- Copyright: Georgi Gerganov e colaboradores

O aviso MIT do whisper.cpp aplica-se ao código nativo obtido na compilação. Não o remova.

## Modelos Whisper GGML

- Origem dos pesos: OpenAI Whisper (https://github.com/openai/whisper)
- Conversão GGML: repositório whisper.cpp / Hugging Face `ggerganov/whisper.cpp`
- Modelo por omissão: `ggml-tiny.bin` (multilingue)
- Licença: MIT

A redistribuição no APK de teste é feita ao abrigo dessa licença. Os modelos **não** são versionados neste repositório Git.

## Android / AndroidX / Kotlin / Compose / Media3 / Room

Distribuídos pela Google e JetBrains sob as licenças Apache 2.0 aplicáveis a cada artefacto Maven. Consulte os POM das dependências para a lista exaustiva. Nenhuma destas bibliotecas envia dados para o exterior na configuração deste projeto.

## Contributor Covenant

O código de conduta adapta o Contributor Covenant 2.1, disponível para utilização nos termos do próprio projeto Contributor Covenant.

## Nota sobre copyleft

Qualquer combinação distribuída da aplicação (APK, código nativo e modelo) deve continuar a oferecer o código-fonte correspondente nos termos da GPL v3.0, incluindo os *scripts* de compilação que obtêm o whisper.cpp e o modelo.
