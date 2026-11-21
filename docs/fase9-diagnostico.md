# Fase 9 — Diagnóstico (fases 7 e 8)

## O que os relatórios mostraram

| Fonte | Cobertura | Erro temporal | Notas |
| --- | --- | --- | --- |
| Fase 7 sintético (seed 7, 500) | 96,7 % | 0 ms vs PCM concatenado | Falhas só em `asr_omit` / `asr_substitute` (comportamento do NW). **Não** é precisão fonética. |
| Fase 8 real pt-PT (seed 8, 200) | 89,9 % no motor actual | divergência vs energia (não é verdade no terreno) | 555 intervalos inválidos, quase todos **OVERLAP**; 108 palavras sem tempo; 7 locutores |

A concordância entre Whisper e o alinhador de energia **não** foi usada como prova de correcção.

## Causas (não só o Whisper)

1. **Timestamps / tokens (Studio Windows)** — `whisper-cli -ml 1` forçava segmentos de 1 carácter. O JNI Android já usava `max_len = 60` + `split_on_word`. Isto explica a maior parte das sobreposições da Fase 8 **no laboratório Windows**, não um bug exclusivo do modelo `tiny`.
2. **Associação texto–transcrição** — o NW recusa pares com similaridade ≤ 0; omissões reais baixam a cobertura (~10 % no real, 23 % nas categorias sintéticas de omissão). Isto é o alinhador, não só o ASR.
3. **Timestamps incorrectos** — o `TimeValidator` **marcava** OVERLAP mas **não recortava** intervalos AUTO, deixando palavras com o mesmo instante de início (reprodução incoerente).
4. **Palavras omitidas** — pontas da frase e pausas longas sem âncora ASR. Inventar tempos aí seria correspondência artificial.
5. **Pausas** — interpolar uma palavra no meio de um silêncio de 2 s seria pior; a Fase 9 só interpola lacunas curtas (≤ 500 ms por palavra).
6. **Palavras repetidas** — o NW já as ordena; não era a falha dominante nos relatórios.
7. **Normalização** — dígitos (`1` vs `um`) e fragmentos (`va`+`mos`) não eram fundidos.
8. **Modelo acústico** — `tiny` continua limitado em fala rápida/ruído. O default passou a `ggml-base.bin` (~142 MB). Continua por medir no R9 Plus; `tiny` ainda se obtém com `-PwhisperModel=ggml-tiny.bin`.

Não houve treino de modelos: não há corpus anotado nem viabilidade no equipamento escolar.
