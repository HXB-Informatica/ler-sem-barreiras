# Fase 9 — Diagnóstico (fases 7 e 8)

## O que os relatórios mostraram

| Fonte | Cobertura | Erro temporal | Notas |
| --- | --- | --- | --- |
| Fase 7 sintético (seed 7, 500) | 96,7 % | 0 ms vs PCM concatenado | Falhas só em `asr_omit` / `asr_substitute` (comportamento do NW). **Não** é precisão fonética. |
| Fase 8 real pt-PT (seed 8, 200) | 89,9 % no motor actual | divergência vs energia (não é verdade no terreno) | 555 intervalos inválidos, quase todos **OVERLAP**; 108 palavras sem tempo; 7 locutores |

A concordância entre o reconhecimento e o alinhador de energia **não** foi usada como prova de correcção.

## Causas

1. **Timestamps** — segmentos demasiado curtos explicam a maior parte das sobreposições da Fase 8 no laboratório, e não um bug só do modelo.
2. **Associação texto–transcrição** — o NW recusa pares com similaridade ≤ 0; omissões reais baixam a cobertura (~10 % no real, 23 % nas categorias sintéticas de omissão). Isto é o alinhador, não só o ASR.
3. **Timestamps incorrectos** — o `TimeValidator` **marcava** OVERLAP mas **não recortava** intervalos AUTO, deixando palavras com o mesmo instante de início (reprodução incoerente).
4. **Palavras omitidas** — pontas da frase e pausas longas sem âncora ASR. Inventar tempos aí seria correspondência artificial.
5. **Pausas** — interpolar uma palavra no meio de um silêncio de 2 s seria pior; a Fase 9 só interpola lacunas curtas (≤ 500 ms por palavra).
6. **Palavras repetidas** — o NW já as ordena; não era a falha dominante nos relatórios.
7. **Normalização** — dígitos (`1` vs `um`) e fragmentos (`va`+`mos`) não eram fundidos.
8. **Modelo acústico** — fala rápida ou ruidosa continua difícil. A precisão no R9 Plus ainda não foi medida.

Não houve treino de modelos: não há corpus anotado nem viabilidade no equipamento escolar.
