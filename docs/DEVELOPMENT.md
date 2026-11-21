# Desenvolvimento

## Versão 0.2.0 — React Native

A partir de **v0.2.0** o desenvolvimento activo da app móvel é em React Native, pasta [`mobile/`](../mobile). Ver [adr/0001-migrar-para-react-native.md](adr/0001-migrar-para-react-native.md) e [releases/v0.2.0.md](releases/v0.2.0.md).

```bash
./scripts/dev-android.sh
```

Isto arranca o AVD `LerTablet` se ainda não estiver a correr, instala dependências npm, sobe o Metro e instala a app. A gravação no emulador usa o microfone do computador (`scripts/host-mic.py`, porta 27183), porque o gravador do Android no emulador fica em silêncio. Depois disso, gravar um ficheiro em `mobile/` actualiza o ecrã (Fast Refresh). Ctrl+C pára o Metro; o emulador continua.

Passos manuais:

```powershell
cd mobile
npm install
npm test
npx react-native run-android
```

A release Android **não** declara `INTERNET`. Só a variante `debug` a inclui, para o Metro. Não publique um APK Kotlin 0.1.x com `versionName` 0.2.0.

As instruções Gradle da linha Kotlin abaixo aplicam-se ao Studio Windows e ao código de referência 0.1.x.

## Requisitos de compilação (Kotlin 0.1.x)

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
./gradlew :studio:run
./gradlew :studio:createDistributable
./gradlew :studio:packageMsi
./gradlew :studio:fetchWhisperWindows
./gradlew :lab-cli:installDist
python test-lab/run.py --seed 7 --count 500 --twice
python test-lab/run_real.py --seed 8 --count 200 --twice
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
- Compose Multiplatform 1.7.1 (módulo `:studio`; não 1.7.3, para permanecer em Kotlin 2.0.21)
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

Os APK publicados nesta fase usam a **chave de depuração** do Android. Destinam-se a testes. Não existem, de propósito, *keystores* nem palavras-passe neste repositório.

A assinatura de produção ficará, no futuro, a cargo do GitHub Actions, com GitHub Secrets. Nomes previstos (ainda não utilizados):

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Não implementar uma chave fictícia. Enquanto estes segredos não existirem, o CI continua a produzir APK de *debug*.

## Publicar uma nova versão

Para a linha 0.2, as notas da versão actual estão em `docs/releases/v0.2.0.md`. Uma Release GitHub com APK React Native só deve ser criada quando houver build instalável. Não reutilize a tag `v0.1.1-beta`.

## Publicar uma versão da linha Kotlin 0.1.x (histórico)

As Releases são criadas automaticamente quando uma tag `v*` é enviada para o GitHub.

1. Atualize `versionName` / `versionCode` em `app/build.gradle.kts` se a versão mudar.
2. Se quiser notas específicas, crie `docs/releases/<tag>.md` (por exemplo `docs/releases/v0.1.1.md`).
3. Envie as alterações para `main`.
4. Crie e publique a tag no **mesmo commit** que contém o código a distribuir:

```bash
git tag -a v0.1.1 -m "Ler sem Barreiras v0.1.1"
git push origin v0.1.1
```

5. O workflow **Android CI** corre testes, gera o APK, verifica o modelo Whisper e a biblioteca JNI, calcula o SHA-256 e cria a Release.
6. Tags com `beta`, `rc`, `alpha` ou `preview` são publicadas como *Pre-release*.

Se a Release da tag já existir, o workflow não substitui artefactos. Não reutilize uma tag já publicada para um APK diferente.

O APK publicado chama-se `ler-sem-barreiras-<tag>.apk`. O checksum fica em `ler-sem-barreiras-<tag>.apk.sha256`.
