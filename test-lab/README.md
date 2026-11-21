# Laboratório de testes sintéticos

Ferramenta **interna de desenvolvimento**. Não entra no APK dos alunos nem no Studio de produção pedagógica.

Um comando gera os casos, corre o **mesmo** `DefaultAlignmentEngine` da aplicação (via `:lab-cli`) e escreve JSON, CSV e HTML.

```powershell
python test-lab/run.py --seed 7 --count 500 --twice
python -m unittest test-lab.tests.test_lab_unit
```

Na raiz do repositório. A seed `7` e 500 casos são o ensaio de referência.

## O que é medido

Os tempos de referência vêm da **concatenação de segmentos PCM** (um tom por palavra + pausa). Não são fronteiras fonéticas de fala natural e **não** substituem testes com voz humana.

O laboratório valida o alinhador (Needleman–Wunsch + estados REVIEW/AUTO) em condições controladas: omissões de ASR, substituições, hesitações, pausas, velocidade e ruído.

## Piper TTS (opcional, não é o gerador por omissão)

| Peça | Licença | Notas |
| --- | --- | --- |
| Piper (rhasspy/piper) | MIT | Motor TTS |
| Voz `pt_PT-tugão-medium` (rhasspy/piper-voices) | MIT no repositório; dataset da voz CC0; base Hi-Fi TTS **CC BY 4.0** | Português europeu; uso comercial exige atribuição ao Hi-Fi TTS |

Não empacotamos Piper nem a voz no APK. A geração por omissão é PCM determinístico para o conjunto de 500 casos ser reproduzível sem descarregar modelos. Se no futuro o `piper` estiver no `PATH` com essa voz, pode sintetizar palavras isoladas e concatená-las — os tempos continuam a ser limites de segmento, não fonética.

## Saídas

`test-lab/work/run-a/reports/` — `summary.json`, `cases.csv`, `report.html`  
Cada caso tem um `id` `s{seed}-{índice}-{categoria}` para reprodução.

Exemplos reduzidos: [docs/test-lab/](../docs/test-lab/).

## Fase 8 — vozes reais e dois motores

```powershell
python test-lab/run_real.py --seed 8 --count 200 --twice
python -m unittest test-lab.tests.test_realvoice
```

Por omissão tenta um parquet derivado de Common Voice (só em `test-lab/work/`, **nunca no Git**). Melhor: corpus oficial descarregado à parte:

```powershell
python test-lab/run_real.py --cv-root C:\dados\cv-corpus-27.0-pt --seed 8 --count 200 --twice
```

O importador filtra `variant=pt-PT` ou sotaques claramente europeus e **exclui** pt-BR. Locutores vão para `dev`/`eval` por hash anónimo. O segundo motor é MFA se existir `mfa` no PATH; senão, alinhamento forçado por energia. A concordância **não** é precisão comprovada.

Metodologia: [docs/test-lab/real-voices.md](../docs/test-lab/real-voices.md).
