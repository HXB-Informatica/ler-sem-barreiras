# Ler sem Barreiras Studio (Windows)

Aplicação **desktop offline** para professores prepararem narrações com microfone USB, no mesmo espírito visual da app Android (azul `#1A4B84`, verde-água, fundo areia).

## Compatibilidade (confirmada nesta fase)

| Peça | Versão | Nota |
| --- | --- | --- |
| Kotlin | 2.0.21 (já no repositório) | **Não** actualizado para 2.1, para não quebrar Android/KSP |
| Compose Multiplatform | **1.7.1** | 1.7.3 recomenda Kotlin 2.1.0 e pode falhar no Skia/Windows com 2.0.21 |
| JDK | 17 | Exigido pelo `jpackage` e pelo resto do projecto |
| Áudio | `javax.sound.sampled` | Sem serviços de nuvem, sem navegador |

O módulo `:studio` é Kotlin/JVM e depende só de `:shared`. **Não** depende de `:audio` Android.

## Como executar

```powershell
.\gradlew.bat :studio:run
```

Distribuição desempacotada (não precisa do WiX):

```powershell
.\gradlew.bat :studio:createDistributable
```

O executável fica em `studio/build/compose/binaries/main/app/`.

Instalador MSI/EXE (`packageMsi` / `packageExe`) exige [WiX Toolset](https://wixtoolset.org/) na máquina. Se o WiX não estiver instalado, o pacote nativo falha; a app continua utilizável via `run` ou `createDistributable`.

## Utilização

1. **Novo projecto** — escolha uma pasta. São criados `studio.json` e `content.json`.
2. Escreva o título e o texto (ou cole de um ficheiro).
3. Escolha o microfone. Os dispositivos USB são assinalados. A app tenta **48 kHz / 24 bits**; se o hardware não der, usa o melhor PCM disponível e explica o *fallback*.
4. Fale e observe o **nível**. Barra vermelha = saturação.
5. **Gravar / Pausar / Terminar**. A pausa usa `TargetDataLine.stop/start`.
6. **Ouvir** reproduz o **original**.
7. O original fica em `audio-original.wav` (PCM, sem filtros). Uma cópia `audio-16k.wav` (mono 16 kHz 16 bits) é gerada só para o reconhecimento futuro. O original não é substituído.
8. O texto é **autosave** (`studio.autosave.json`). Se o Studio fechar a meio, a reabertura recupera esse ficheiro.

## Verificação nesta máquina (17 nov 2026)

- Compilação `:studio:compileKotlin` e testes `:studio:test` — **ok**
- `LiveCaptureTest` gravou ~400 ms com um mixer Java Sound presente — **ok**
- Enumeração Windows: microfone **HyperX Cloud III S Wireless** (áudio USB) e controlador HD — **ok**
- UI executável aberta; seleção do HyperX; formato negociado **48 kHz / 16 bits mono** (o hardware **não** ofereceu 24 bits; mensagem de fallback visível) — **ok**
- Distribuição: `studio/build/compose/binaries/main/app/LerSemBarreirasStudio/LerSemBarreirasStudio.exe`
- Instalador: `studio/build/compose/binaries/main/msi/LerSemBarreirasStudio-0.4.0.msi` (WiX descarregado pelo Gradle)

### Ainda pendentes (físicos)

- Gravação contínua de **vários minutos** (o teste automático de 3 min escreve silêncio em disco, não voz ao vivo)
- Desligar o dongle USB **a meio** da captura
- Confirmar no Explorador de ficheiros as propriedades do WAV depois de uma sessão longa
- Instalar o MSI noutro PC Windows 10/11 e gravar sem Internet
- Pausar / repetir uma narração completa na UI e ouvir o original

## Classificação da Fase 4

**PASS WITH ISSUES**

A app desktop existe, compila, corre offline, grava PCM em fluxo, preserva o original, gera a cópia 16 kHz, trata fallbacks de formato e produz EXE + MSI. Issues: 48 kHz/24 bits não está disponível nestes dispositivos (fallback documentado); a sessão longa e a desconexão USB não foram ensaiadas ao vivo.

A Fase 5 **não** foi iniciada nesta nota da Fase 4; ver secção seguinte.

## Fase 5 — Sincronização no Studio

O professor grava (ou importa), escreve o texto, corre o alinhamento **local** e corrige na mesma janela. Não é preciso tablet.

### Reconhecimento

O Studio cria as palavras do texto para correção manual. O reconhecimento automático da narração está na app móvel, descrito em [OFFLINE_AI.md](OFFLINE_AI.md).

### Editor

Reutiliza `SyncEditor`, `DefaultAlignmentEngine` e `WordTiming` de `:shared`. A UI Compose Desktop não depende de `:feature-teacher` nem de Media3.

- Forma de onda com zoom 1×/2×/4× e deslocamento horizontal
- Destaque da palavra em reprodução; selecção a rato e teclado
- Arrastar limites, ±50/±100 ms, Início/Fim aqui
- Ouvir tudo / palavra / a partir da selecção; 0,5× 0,75× 1×; repetir intervalo
- Validar palavra, desfazer/refazer, autosave, conflitos e resumo (alinhadas / revisão / verificadas)

Importação de áudio: WAV / AIFF / AU via `javax.sound` (PCM). MP3 não está incluído de propósito (evita descodificador extra).

### Teste principal «Olá um dois três»

Cobertura automatizada em `StudioAlignmentServiceTest`: quatro objectos de palavra, «dois»/«três» em REVIEW, correção + VERIFIED, guardar e reabrir `content.json`.

## Classificação da Fase 5

**PASS WITH ISSUES**

Comprovado em testes: quatro palavras de «Olá um dois três»; correção de REVIEW; persistência; silêncio; texto diferente; omissões/repetidas; falha do motor; forma de onda de 12 s.

Não comprovado em sessão pedagógica completa: narração longa ao vivo, destaque visual ouvido pelo professor, arrastar marcadores na UI, instalação MSI noutro PC offline.

A Fase 6 (formato `.lsb`) está documentada em [lsb-format.md](lsb-format.md).

## Formato da pasta do projecto

```
minha-licao/
  studio.json
  studio.autosave.json   (temporário)
  content.json           (lersembarreiras-content, para a app Android)
  audio-original.wav
  audio-16k.wav
```
