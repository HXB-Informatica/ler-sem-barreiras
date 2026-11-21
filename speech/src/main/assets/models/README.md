Os modelos Whisper GGML são obtidos durante a compilação e não são versionados no Git.

Por omissão a tarefa `fetchWhisperModel` descarrega `ggml-base.bin` (multilingue, inclui português) a partir de:

https://huggingface.co/ggerganov/whisper.cpp

Não utilizar variantes `.en`. Para voltar ao modelo `tiny`, compile com:

```
./gradlew :app:assembleDebug -PwhisperModel=ggml-tiny.bin
```
