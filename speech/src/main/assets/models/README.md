Os modelos Whisper GGML são obtidos durante a compilação e não são versionados no Git.

Por omissão a tarefa `fetchWhisperModel` descarrega `ggml-tiny.bin` (multilingue, inclui português) a partir de:

https://huggingface.co/ggerganov/whisper.cpp

Não utilizar variantes `.en`. Para experimentar o modelo `base`, compile com:

```
./gradlew :app:assembleDebug -PwhisperModel=ggml-base.bin
```
