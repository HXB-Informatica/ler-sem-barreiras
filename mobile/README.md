# Ler sem Barreiras — app móvel (v0.2.0)

Aplicação React Native (TypeScript) para Android. Substitui a linha Kotlin 0.1.x no desenvolvimento activo.

## Requisitos

- Node.js 22.11 ou superior
- JDK 17
- Android SDK (a versão vem do modelo React Native 0.88)

A **release** da app instalada **não** tem permissão `INTERNET`. As builds de depuração incluem-na só para o Metro.

## Comandos

Iteração rápida no emulador (SDK, `LerTablet`, Metro, Fast Refresh):

```bash
./scripts/dev-android.sh
```

Ou, a partir de `mobile/`: `npm run dev`. Ctrl+C pára o Metro; o emulador fica a correr.

```powershell
cd mobile
npm install
npm test
npx react-native start
npx react-native run-android
```

APK de depuração:

```powershell
cd mobile\android
.\gradlew.bat assembleDebug
```

O APK fica em `mobile/android/app/build/outputs/apk/debug/`.

## O que já existe

- Modo Professor: criar texto, gravar WAV PCM 16 kHz, importar áudio WAV, importar `.lsb`, guardar localmente
- Modo Aluno: três modos de leitura, letra, contraste, destaque pela posição do leitor
- Alinhador TypeScript (Needleman–Wunsch, lacunas curtas, validador de intervalos) com testes
- Módulos nativos Android: ficheiros, ZIP `.lsb`, áudio
- Sincronização automática local com whisper.cpp (`ggml-tiny.bin`, língua `pt`)

## O que falta para paridade com a 0.1

- Compilar whisper.cpp neste APK e devolver palavras com timestamps
- Editor visual de intervalos (forma de onda)
- Validação no HOTWAV TAB R9 Plus em modo avião
