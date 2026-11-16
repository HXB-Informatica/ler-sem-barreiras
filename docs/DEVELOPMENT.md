# Desenvolvimento

## Requisitos de compilação

- JDK 17
- Android SDK (compileSdk 35, build-tools 35)
- NDK 27.2.12479018
- CMake 3.22.1
- Ligação à Internet **apenas na máquina de compilação**, para o Gradle, o whisper.cpp e o modelo GGML

A aplicação gerada não precisa de Internet.

## Primeira compilação

```bash
./gradlew :app:assembleDebug
./gradlew test
```

Tarefas úteis do módulo `speech`:

- `fetchWhisperCpp` — obtém whisper.cpp `v1.9.5`
- `fetchWhisperModel` — obtém `ggml-tiny.bin` (ou outro, via `-PwhisperModel=`)

Os artefactos descarregados ficam em:

- `speech/src/main/cpp/third_party/whisper.cpp/`
- `speech/src/main/assets/models/`

Ambos estão no `.gitignore`. Não os publique no Git.

## Modelo alternativo

```bash
./gradlew :app:assembleDebug -PwhisperModel=ggml-base.bin
```

Use apenas variantes **multilingues**. Não utilize modelos `.en`.

## Estrutura Gradle

Catálogo de versões em `gradle/libs.versions.toml`. Versões escolhidas por estabilidade conjunta:

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- Media3 1.5.1
- Room 2.6.1
- NDK 27.2.12479018

## Execução no tablet

1. Ative a depuração USB ou copie o APK.
2. Instale `app/build/outputs/apk/debug/app-debug.apk`.
3. No HOTWAV TAB R9 Plus, confirme ARM64 (`adb shell getprop ro.product.cpu.abi`).
4. Teste em modo avião o ciclo completo: criar, gravar, sincronizar, ler.

## Convenções

- Kotlin, corrotinas e `Flow`.
- Sem Hilt nesta versão; dependências via `AppContainer`.
- Sem simulações silenciosas: se o JNI falhar, a interface mostra erro.
- Comentários e documentação em português de Portugal.

## Assinatura

O APK de teste usa a chave de depuração do Android. Não existe estratégia de chave de produção neste repositório, de propósito: chaves reais nunca devem ser versionadas.
