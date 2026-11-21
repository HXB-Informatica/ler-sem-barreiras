# Instalação e utilização

A **v0.2.0** é a linha React Native (`mobile/`). Ainda **não** há Release GitHub com esse APK. Para gerar um APK de depuração: `cd mobile\android` e `.\gradlew.bat assembleDebug`. A última beta Kotlin publicada é a v0.1.1 (linha congelada). Ver [releases/v0.2.0.md](releases/v0.2.0.md).

## Aplicação Android Kotlin 0.1.x (alunos / professores) — legado

1. Compile com JDK 17: `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat`).
2. Instale `app/build/outputs/apk/debug/app-debug.apk` no tablet (API 26+).
3. Ative o modo avião: criar texto, gravar, sincronizar e ler **não** usam rede.
4. Importe um `.lsb` do Studio se o ficheiro estiver no dispositivo (USB, pasta, Bluetooth).

Requisitos de compilação: [DEVELOPMENT.md](DEVELOPMENT.md). Tablet de referência: HOTWAV TAB R9 Plus (ainda por validar em equipamento).

## Ler sem Barreiras Studio (Windows)

1. `.\gradlew.bat :studio:fetchWhisperWindows`
2. `.\gradlew.bat :studio:run` ou `:studio:createDistributable`
3. Microfone USB: gravar WAV 16 kHz, sincronizar, corrigir no editor, guardar o projecto, exportar `.lsb`.
4. Sem Internet em tempo de execução.

Formato de troca: [lsb-format.md](lsb-format.md).

## Laboratório interno (não vai para o APK)

```powershell
python test-lab/run.py --seed 7 --count 500 --twice
python test-lab/run_real.py --seed 8 --count 200
```

Common Voice: `--cv-root` com `validated.tsv` e `clips/`, ou o parquet local em `test-lab/work/` (gitignore).

## Distribuição open source

- Licença: GPL-3.0-only (`LICENSE`).
- Código em Git; Releases só **depois** de validação e autorização explícita (não nesta fase).
- Não versionar modelos GGML, whisper-cli, áudios de terceiros nem keystores.
- Contribuições: [CONTRIBUTING.md](../CONTRIBUTING.md). Conduta: [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md).
