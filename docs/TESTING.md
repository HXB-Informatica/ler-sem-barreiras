# Testes

## Testes unitários (executados nesta versão)

Cobertura inicial, sem gravar vozes de crianças:

- separação e normalização de palavras (`WordTokenizerTest`)
- correspondência com timestamps, palavras repetidas, pontuação, omissões, hesitações, pausas e falhas totais (`WordAlignerTest`)
- validação de intervalos temporais (`TimeValidatorTest`)
- leitura/escrita WAV e reamostragem (`WavPcmTest`)
- persistência Room em memória e formato JSON (`ContentRepositoryTest`, Robolectric)
- editor visual: frase «Olá um dois três», persistência MANUAL/VERIFIED, undo, palavras repetidas, pausas e sobreposições (`SyncEditorTest`)
- forma de onda com número fixo de picos em gravação longa (`WaveformExtractorTest`)
- Studio Windows: WAV em fluxo (incl. 3 min em disco), 24 bits no cabeçalho, clipping, projecto/autosave, cópia 16 kHz, enumeração de mixers, captura breve se existir microfone (`:studio:test`)
- Studio sincronização: «Olá um dois três», silêncio, texto diferente, omissões/repetidas, falha do motor, forma de onda longa (`StudioAlignmentServiceTest`)
- Pacote `.lsb`: ida e volta com quatro palavras e áudio, parágrafos, checksum, versão futura, path traversal, ZIP com demasiadas entradas, importação Android sem apagar conteúdos (`LsbArchiveTest`, `ContentRepositoryTest`)
- Laboratório sintético: `python -m unittest test-lab.tests.test_lab_unit` e `python test-lab/run.py --seed 7 --count 500 --twice` (alinhador da app, 500 casos, duas corridas)
- Vozes reais: `python -m unittest test-lab.tests.test_realvoice` e `python test-lab/run_real.py --seed 8 --count 200 --twice` (Common Voice pt-PT, dois motores, regressão)
- Fase 9: overlap reparado, dígitos, fragmentos, lacunas curtas vs pausas longas (`WordAlignerTest`, `TimeValidatorInspectTest`, `WordTokenizerTest`)

Relatório Fase 9 (antes/depois): [fase9-antes-depois.md](test-lab/fase9-antes-depois.md).

Executar:

```bash
./gradlew test
./gradlew :studio:test
```

## Testes de integração

Preparados no código da aplicação (fluxo real, não simuladores silenciosos):

- importação de áudio via `AudioImporter`
- gravação via `AudioRecorder`
- carregamento do modelo e JNI
- extração de timestamps e alinhamento
- reprodução Media3 com destaque pela posição do leitor
- reabertura de conteúdos a partir da base local

Estes testes exigem dispositivo ou emulador. **Não foram executados automaticamente neste ambiente de desenvolvimento** até haver um equipamento ligado. Não devem ser apresentados como aprovados sem corrida real.

## Testes no HOTWAV TAB R9 Plus

Ainda **não realizados**. Quando o tablet estiver disponível, registar:

| Métrica | Objetivo de engenharia | Resultado |
| --- | --- | --- |
| Palavras a ≤ 250 ms do instante real | ≥ 95 % | por medir |
| Tempo de processamento / duração do áudio | < 3× | por medir |
| RAM durante `base` | compatível com 8 GB físicos | por medir |
| Estabilidade (sem crash, cancelamento) | estável | por medir |
| Fluidez da reprodução | sem desvio acumulado | por medir |

Usar apenas áudio sintético ou narrações de adultos com autorização. **Nunca** publicar gravações de alunos.

Procedimento sugerido:

1. Instalar o APK de *debug*.
2. Ativar modo avião.
3. Criar um texto curto em português europeu (por exemplo 20–40 palavras).
4. Gravar com ritmo de leitura escolar.
5. Sincronizar e anotar o tempo, com `adb shell dumpsys meminfo pt.lersembarreiras.app.debug`.
6. Reproduzir nos modos A, B e C.
7. Encerrar a aplicação, reabrir e confirmar que a sincronização não volta a correr.
8. Repetir com um áudio importado.

## CI

O *workflow* `.github/workflows/android.yml` instala NDK/CMake, corre `./gradlew test` e gera o APK de *debug* como artefacto.
