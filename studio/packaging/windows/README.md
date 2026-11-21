# Motor Whisper para o Studio Windows

Esta pasta recebe, em tempo de compilação, o **whisper-cli.exe** x64 (`whisper.cpp` v1.9.1) e o modelo `ggml-base.bin`.

Não reutiliza a biblioteca JNI Android (`libwhisper-jni.so`).

```powershell
.\gradlew.bat :studio:fetchWhisperWindows
```

Instalação manual: copie `whisper-cli.exe`, as DLL que o acompanham e `ggml-base.bin` para:

- esta pasta, ou
- `%USERPROFILE%\.lersembarreiras\whisper`, ou
- o diretório indicado por `LER_SEM_BARREIRAS_WHISPER_DIR`

A aplicação só executa o processo localmente, offline.
