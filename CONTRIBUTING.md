# Como contribuir

Obrigado por considerar contribuir para o Ler sem Barreiras. Este é um projeto educativo da Escola Gil Vicente, com a HXB como parceiro tecnológico. As contribuições de programadores externos são bem-vindas.

## Antes de começar

1. Leia o [README](README.md), o [código de conduta](CODE_OF_CONDUCT.md), a [arquitetura](docs/ARCHITECTURE.md) e a [decisão React Native](docs/adr/0001-migrar-para-react-native.md) (v0.2.0). Contribuições novas à **app móvel** devem seguir essa linha, não alargar o Kotlin 0.1.x salvo correcções pontuais do Studio ou do formato `.lsb`.
2. Abra uma *issue* a descrever a alteração, salvo correções óbvias.
3. Não inclua dados de alunos, gravações reais, credenciais ou ficheiros de assinatura.

## Regras de produto

Qualquer contribuição deve preservar:

- funcionamento integralmente offline;
- ausência da permissão Android `INTERNET`;
- ausência de publicidade, analytics, telemetria e contas obrigatórias;
- código sob `GPL-3.0-only`, sem dependências proprietárias obrigatórias;
- interface desta fase em português europeu, com a arquitetura preparada para futuras traduções.

## Ambiente de desenvolvimento

Instruções em [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Em resumo:

```bash
cd mobile && npm test
cd mobile/android && ./gradlew assembleDebug
```

A linha Kotlin 0.1.x (`./gradlew test`) permanece para o Studio e para o código de referência.

## Processo de *pull request*

1. Bifurque o repositório ou crie um ramo a partir de `main`.
2. Faça alterações pequenas e revistáveis.
3. Inclua testes para lógica de alinhamento, normalização de texto ou persistência sempre que possível.
4. Documente limitações em vez de as esconder atrás de simulações.
5. Garanta que o CI (GitHub Actions) conclui a compilação e os testes unitários.
6. No *pull request*, explique o motivo da alteração e como foi testada.

## Direitos de autor

Ao contribuir, licencia o seu trabalho nos termos da GNU GPL v3.0 (`GPL-3.0-only`). Os autores mantêm os seus direitos; o projeto não presume cessão de titularidade ainda não formalizada. Não envie código que não possa ser publicado sob esta licença.

## Comunidade

Utilize as *issues* do GitHub para defeitos e propostas. Não publique informação pessoal de crianças, professores ou escolas.
