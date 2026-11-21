# Fase 9 — Desempenho antes / depois

Conjuntos: sintético seed 7 (500, lab interno) e real seed 8 (200 clips pt-PT, **eval** 7 locuções / **dev** 193; não se reoptimizou o alinhador só no eval). Referências sintéticas = PCM concatenado, **não** fonética.

## Sintético

| Métrica | Fase 7 (antes) | Fase 9 (depois) |
| --- | --- | --- |
| Palavras alinhadas | 96,69 % | **98,06 %** |
| `asr_omit` | 77,1 % | **85,5 %** |
| `asr_substitute` | 77,7 % | **88,0 %** |
| Intervalos inválidos | 0 | 0 |
| Erro vs PCM (média) | 0 ms | 0 ms |
| Reprodução (2 corridas) | sim | sim (`068bd826…e67c`) |
| RSS Python | n/d | ver `summary.json` `memory` |

A subida em omissões/substituições vem da interpolação **curta** (REVIEW/ALIGNED), não de fingir que o ASR ouviu a palavra. Pausas longas continuam sem tempo.

## Real (Common Voice pt-PT, reconhecimento + alinhador)

| Métrica | Fase 8 | Fase 9 |
| --- | --- | --- |
| Mapeadas (motor actual) | 89,9 % | **90,3 %** |
| Palavras sem tempo | 108 | 104 |
| Sobreposições (VAD/comparador) | 555 | **0** |
| Outros intervalos inválidos (zero, ordem, …) | misturados nas 555 | 551 (maioritariamente duração residual / fim de áudio) |
| Divergência elevada vs 2.º motor | 19 | 4 |
| Concordância elevada | 39 / 200 | 41 / 200 |
| Falhas técnicas | 0 | 0 |
| Tempo médio do alinhador | ~2,3 s/clip | ~2,4 s/clip (mesma ordem) |
| Fingerprint | `698879c5…` | `acfd0829…` (esperado: reparação dos intervalos) |

Meta de desenvolvimento 95 % em gravações limpas: **não atingida** neste corpus real (90,3 %). Erro temporal abaixo de 200 ms vs referência humana: **não medido**.

## O que foi rejeitado

- A precisão no tablet alvo continua sem evidência no R9 Plus.
- Treino de modelos: sem corpus anotado nem viabilidade escolar.
- Preencher pausas longas e pontas da frase: seria correspondência artificial.

## Android / Studio / exercício completo

Ver [LIMITATIONS.md](../LIMITATIONS.md). Não se declara PASS nesses fluxos.
