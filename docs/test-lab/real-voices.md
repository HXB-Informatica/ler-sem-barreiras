# Vozes reais e comparação de alinhadores

Ferramenta interna. Não entra no APK. **Não** publique clips de terceiros neste repositório.

## Licença

[Mozilla Common Voice](https://commonvoice.mozilla.org/) em português está sob **CC-0**. Respeite a ficha de dados da versão que descarregar. Este projecto não redistribui as gravações.

A descarrega automática opcional usa um parquet empacotado em Hugging Face (`remynd/common_voice_pt_pt`), tratado como **derivado** de Common Voice: só fica em `test-lab/work/` (gitignore). O importador **não aceita** sotaques claramente brasileiros mesmo que o conjunto se chame `pt_pt`.

Prefira o corpus oficial (`validated.tsv` + `clips/`) com o campo `variant=pt-PT` (na versão 27 há milhares de clips pt-PT).

## Motores

| Motor | O que é | Independência |
| --- | --- | --- |
| Actual | `DefaultAlignmentEngine` via `:lab-cli` | O da aplicação |
| MFA | Montreal Forced Aligner + modelo português, se `mfa` estiver instalado | Preferido |
| `energy_dtw` | Energia RMS + DTW de durações ortográficas | Usado quando MFA não está no PATH. Não é fonética; justifica-se para validação cruzada em Windows sem Conda/Kaldi |

Verificações de fala/silêncio são um **terceiro** sinal (RMS), não um dos dois alinhadores.

## Classificação

`falha_tecnica` · `incompatibilidade_texto_audio` · `intervalo_invalido` · `palavra_ausente` · `divergencia_elevada` · `divergencia_moderada` · `concordancia_elevada`

A concordância **não** prova alinhamento correcto. Sem referência temporal fiável (anotação humana ou MFA validado) **não** se estima precisão real. Relatórios distinguem «erros detectados» de «precisão comprovada».

Não se tentam identificar locutores: só `locutor_anon`.
