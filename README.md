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

## Descarregar e instalar a aplicação

A forma prevista de obter a aplicação é a página de [GitHub Releases](https://github.com/HXB-Informatica/ler-sem-barreiras/releases). Qualquer pessoa pode descarregar o APK, **sem conta GitHub** e sem compilar o código.

**Versão atual de testes:** [Ler sem Barreiras v0.1.0 Beta](https://github.com/HXB-Informatica/ler-sem-barreiras/releases/tag/v0.1.0-beta)

Esta versão é **experimental**. Destina-se a testes técnicos, não a utilização pedagógica em produção. O APK está assinado com a chave de depuração do Android; uma assinatura de produção será configurada mais tarde, através de GitHub Secrets, sem nunca versionar chaves privadas.

### Instalação num tablet Android

1. Abra a [página de Releases](https://github.com/HXB-Informatica/ler-sem-barreiras/releases) no tablet ou noutro computador.
2. Descarregue `ler-sem-barreiras-v0.1.0-beta.apk` (ou a versão mais recente publicada).
3. Se o Android bloquear a instalação, permita instalações a partir do navegador ou do gestor de ficheiros usado para o descarregamento.
4. Abra o APK, instale e inicie **Ler sem Barreiras**.
5. Opcional: confira o ficheiro `.sha256` publicado na mesma Release.

Depois de instalada, a aplicação funciona **integralmente offline**, inclusive em modo avião: criação de conteúdos, gravação, sincronização local e leitura não precisam de Internet.

O equipamento de referência para validação é o **HOTWAV TAB R9 Plus** (Android 14). Nesta beta, a sincronização nesse tablet ainda não foi validada em equipamento físico.

Compilação local, se preferir gerar o APK no seu computador:

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
