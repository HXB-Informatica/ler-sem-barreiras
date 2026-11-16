# Motor de IA offline

## Escolha inicial

A versão 0.1 integra [whisper.cpp](https://github.com/ggml-org/whisper.cpp) **v1.9.5** por JNI, a correr em CPU.

| Candidato | Tamanho aproximado | Notas |
| --- | --- | --- |
| `ggml-tiny.bin` (multilingue) | ~75 MB | **Selecionado** para a v0.1: cabe no APK, cobre português, adequado ao T7280 |
| `ggml-tiny-q5_1.bin` / `q8_0` | ~31–42 MB | Possível redução de tamanho; qualidade temporal a validar |
| `ggml-base.bin` (multilingue) | ~142 MB | Melhor precisão esperada; mais RAM e tempo |
| `*-en` | — | **Excluídos**: não suportam português |

A seleção de `tiny` é uma decisão de engenharia da v0.1, não um resultado de *benchmark* no HOTWAV R9 Plus. Esse ensaio ainda tem de ser feito no equipamento real. Ver [TESTING.md](TESTING.md).

## Licenças

- whisper.cpp: MIT (ggml-org)
- Modelos Whisper originais: MIT (OpenAI)
- Redistribuição do `ggml-tiny.bin` no APK: permitida pela licença MIT, com preservação do aviso de copyright

O processo de empacotamento **não** coloca o binário no Git. A tarefa Gradle `fetchWhisperModel` obtém o ficheiro na compilação a partir de [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp).

## Integração

- CMake em `speech/src/main/cpp/CMakeLists.txt`
- JNI em `speech/src/main/cpp/jni.cpp`
- Kotlin: `NativeWhisperEngine` implementa `SpeechRecognitionEngine`
- Áudio interno: PCM 16-bit mono a 16 kHz
- Língua forçada: `pt`
- GPU desligada (`use_gpu = false`)
- *threads*: entre 2 e 6, conforme o dispositivo

O texto original é passado como `initial_prompt` (truncado) para enviesar o reconhecimento, não para o substituir.

## Timestamps

As marcas temporais ao nível de *tokens* do whisper.cpp são **experimentais**. Nesta versão:

- `token_timestamps = true`
- `split_on_word = true`
- tempos nativos em unidades de 10 ms, convertidos para milissegundos
- se um *token* não tiver intervalo válido, usa-se o intervalo do segmento e a palavra é marcada para revisão

Não são inventados timestamps com aparência de precisão de palavra quando o motor só fornece um segmento. Palavras sem correspondência fiável no alinhamento ficam com `startMs`/`endMs` nulos e `needsReview = true`.

## Alinhamento

`WordAligner` aplica Needleman–Wunsch entre as palavras do texto (grafia original preservada, comparação normalizada) e as palavras reconhecidas. Cobre:

- pontuação e maiúsculas
- acentuação (NFD + remoção de diacríticos)
- palavras repetidas
- omissões do narrador
- hesitações extra reconhecidas
- pequenos erros de ASR (distância de Levenshtein)

## Cancelamento e progresso

A sincronização corre fora da *UI thread*. O professor vê progresso e pode cancelar. O JNI consulta `abort_callback` do whisper.cpp.

## Limitações conhecidas da v0.1

- `tiny` comete erros em fala rápida, sobreposta ou muito ruidosa.
- Português europeu escolar (voz de professor, ritmo pausado) é o cenário alvo; não foi ainda medido no R9 Plus.
- O APK cresce ~75 MB por causa do modelo.
- Só são compiladas as ABI `arm64-v8a` e `x86_64`.
- A sincronização deve decorrer com a aplicação em primeiro plano.
