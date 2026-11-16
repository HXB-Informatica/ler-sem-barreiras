# Ler sem Barreiras

Aplicação Android educativa, gratuita e **integralmente offline**, para sincronizar um texto com a respetiva narração áudio. Destina-se a apoiar crianças com dificuldades de leitura, comunicação e aprendizagem.

**Versão:** 0.1.0

## Responsabilidade do projeto

- **Instituição responsável:** Agrupamento de Escolas Gil Vicente, Portugal.
- **Parceiro tecnológico:** HXB Informática e Tecnologia, Unipessoal Lda., que participa voluntariamente e sem custos.

O projeto pertence à iniciativa da escola. A HXB é identificada como parceiro tecnológico e não reivindica titularidade exclusiva. Os direitos de autor sobre o código pertencem aos respetivos autores e colaboradores, nos termos da [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`). Não se presume qualquer cessão de direitos ainda não formalizada.

## Princípios

- Gratuita para qualquer escola, instituição ou utilizador.
- Código-fonte aberto e contribuições externas bem-vindas.
- Funcionamento 100 % local, inclusive em modo avião.
- Sem contas, subscrições, publicidade, analytics, telemetria ou serviços pagos.
- Sem recolha ou transmissão de dados de alunos.
- Sem dependência de infraestruturas da HXB.
- Sem componentes proprietários obrigatórios para executar a aplicação.

## O que a versão 0.1 faz

### Modo Professor

1. Criar um conteúdo com título e texto.
2. Gravar a narração com o microfone do tablet ou importar um ficheiro áudio.
3. Reproduzir e confirmar o áudio.
4. Iniciar a sincronização automática **no próprio dispositivo**.
5. Ver as palavras alinhadas com os tempos da voz.
6. Corrigir manualmente os tempos que o motor não conseguiu alinhar com confiança.
7. Guardar o conteúdo para utilização offline.

### Modo Aluno

- Escolher um texto já sincronizado.
- Ouvir a gravação original, com pausa e recomeço.
- Ver a palavra pronunciada destacada, usando o relógio de posição do leitor de média.
- Três modos de apresentação: texto completo, palavra a palavra e leitura progressiva.
- Ajuste do tamanho das letras e modo de elevado contraste.

## Motor de IA offline

A sincronização usa [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT), integrado por JNI, e o modelo Whisper multilingue `tiny` (`ggml-tiny.bin`, MIT). O modelo é obtido **durante a compilação** e fica dentro do APK. A aplicação **não** tem permissão `INTERNET` e não descarrega nada em tempo de execução.

O objetivo do motor não é substituir o texto do professor: é estimar *quando* cada palavra do texto original é pronunciada na gravação. Os detalhes estão em [docs/OFFLINE_AI.md](docs/OFFLINE_AI.md).

## Requisitos

- Tablet ou telemóvel Android 8.0 (API 26) ou superior.
- Equipamento de referência: **HOTWAV TAB R9 Plus** (Android 14, Unisoc T7280, 8 GB de RAM física, ecrã 11'', 1920 × 1200).
- Microfone, para gravação no Modo Professor.

## Instalação do APK de teste

Ainda não existe uma publicação assinada de produção. O APK de depuração é gerado pelo GitHub Actions e pode ser instalado para testes internos.

1. Abra a ação **Android CI** no GitHub e descarregue o artefacto `ler-sem-barreiras-debug`.
2. No tablet, permita a instalação a partir da origem escolhida.
3. Instale o APK e abra **Ler sem Barreiras**.
4. Confirme que o tablet pode ficar em modo avião: criação, gravação, sincronização e leitura continuam a funcionar.

Compilação local:

```bash
./gradlew :app:assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/`.

No Windows (PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug
```

A primeira compilação descarrega o código do whisper.cpp e o modelo `ggml-tiny.bin`. Estas transferências ocorrem **só na máquina de compilação**, nunca na aplicação instalada.

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md)
- [Desenvolvimento](docs/DEVELOPMENT.md)
- [Motor offline](docs/OFFLINE_AI.md)
- [Testes](docs/TESTING.md)
- [Roteiro](docs/ROADMAP.md)
- [Licenças de terceiros](docs/THIRD_PARTY_LICENSES.md)
- [Como contribuir](CONTRIBUTING.md)
- [Código de conduta](CODE_OF_CONDUCT.md)
- [Segurança](SECURITY.md)

## Licença

Este programa é software livre: pode redistribuí-lo e/ou modificá-lo nos termos da GNU GPL v3.0. Consulte o ficheiro [LICENSE](LICENSE). Não há qualquer garantia.
