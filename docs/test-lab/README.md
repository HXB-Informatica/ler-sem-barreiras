# Exemplos de relatório (Fase 7)

Gerados com `python test-lab/run.py --seed 7 --count 500 --twice`.

| Ensaio | Casos | Palavras alinhadas | Fingerprint SHA-256 |
| --- | --- | --- | --- |
| A e B | 500 | 96,69 % | `fb88f7d236c58ff7c9379f8358f83cefba4a14427d5a0a2c2ca943ca06edeef0` |

Os dois ensaios coincidiram. Os identificadores dos casos têm a forma `s0007-NNNN-categoria`.

Ficheiros: [example-summary.json](example-summary.json), [example-report.html](example-report.html).

O erro médio de timestamp neste ensaio é **0 ms** porque, por omissão, as palavras reconhecidas injectadas no alinhador usam os mesmos limites PCM da concatenação. As categorias `asr_omit` e `asr_substitute` baixam a percentagem de alinhamento (palavras em falta). Isto valida o motor de alinhamento, não a precisão fonética nem o Whisper sobre o WAV.

Os tempos de referência **não** são fronteiras de fala natural.
