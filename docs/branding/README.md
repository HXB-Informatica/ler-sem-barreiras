# Identidade visual

A paleta e o ícone da aplicação seguem o pack gráfico v1. O ZIP original (`Ler_sem_Barreiras_Pack_Grafico_v1.zip`) **não estava disponível nesta máquina** no momento da integração; usaram-se:

- o ícone aprovado `docs/icone_aprovado_original.png`;
- a paleta definida em `docs/PROMPT_CURSOR.md`: `#0E5CA8`, `#0B4F8F`, `#0FAF9F`, `#A9D8F7`, `#FFC24D`, `#8B6BD9`, `#1F2937`, `#F7FAFC`.

## Ícone

O original é um quadrado arredondado RGB 1254×1254, com cantos quase brancos. Os recursos Android:

- **clássicos** (`mipmap-*/ic_launcher.png` e `ic_launcher_round.png`): o desenho aprovado, com fundo azul da arte;
- **adaptativos** (`mipmap-anydpi-v26` + camadas `ic_launcher_foreground` / `ic_launcher_background`): o mesmo desenho recuado na zona segura, fundo `#0B4F8F` amostrado da arte.

Não se usou o SVG simplificado do pack (ausente). A diferença visual face ao original é sobretudo o recorte adaptativo do Android 8+, não uma reinterpretação do símbolo.

Regenerar densidades:

```powershell
python .\scripts\generate_android_icons.py
```

## Tipografia

Nunito não foi empacotada: a licença do ficheiro de fonte não veio no repositório. A UI usa a família sem serifa do sistema.

## Apoiantes

A página «Sobre o projeto» lê uma lista vazia até a escola aprovar entidades (incluindo qualquer menção à Sonae). Não se incluem logótipos institucionais não fornecidos.
