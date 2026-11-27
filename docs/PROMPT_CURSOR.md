# CURSOR — INTEGRAR IDENTIDADE VISUAL E REESTRUTURAR A UI DO LER SEM BARREIRAS

## Missão
Trabalha diretamente no repositório Android existente **https://github.com/HXB-Informatica/ler-sem-barreiras** (pasta local `D:\Projecto Ler sem Barreiras`). A aplicação já tem gravação, sincronização offline com whisper.cpp, Room, Media3/ExoPlayer e dois modos (Professor e Aluno). **Não recries o projeto do zero.** Aplica o pack gráfico `Ler_sem_Barreiras_Pack_Grafico_v1.zip` e moderniza a interface, preservando todas as funcionalidades técnicas atuais.

## Antes de implementar
1. Inspeciona o repositório, branch, testes, estado Git e UI Compose atual. Não sobrescrevas alterações do utilizador.
2. Descompacta o pack gráfico fornecido numa pasta de trabalho. Lê primeiro `README.md`, `03_Especificacoes/SISTEMA_VISUAL.md`, `ESTRUTURA_ECRAS.md`, `SOBRE_O_PROJETO.md` e `ACESSIBILIDADE_E_QA.md`.
3. O ícone em `01_Identidade/icone_aprovado_original.png` é o original **aprovado**; a prancha é referência conceptual, não layout para copiar literalmente. O símbolo SVG é uma **adaptação simplificada**, útil para ícones adaptativos. Mantém fidelidade ao original; se a diferença visual for significativa, ajusta a implementação e relata-a.
4. Faz inventário das rotas e funcionalidades existentes; prepara um plano breve de alteração UI/tema/navegação sem interferir na camada de áudio, dados ou sincronização.

## Objetivos visuais
- Identidade simples, inclusiva e moderna. Paleta exata `01_Identidade/paleta.json`: azul principal `#0E5CA8`, azul profundo `#0B4F8F`, teal `#0FAF9F`, azul claro `#A9D8F7`, amarelo `#FFC24D`, roxo `#8B6BD9`, texto `#1F2937`, fundo `#F7FAFC`.
- Integrar o ícone no launcher Android, splash Android 12+, marca do ecrã de entrada e ecrãs institucionais. Os recursos redimensionados e XML de exemplo estão em `02_Android/res/`.
- Usar typography legível, de preferência Nunito só se a licença e a distribuição estiverem corretas; caso contrário usar fonte do sistema. Oferecer letras grandes no Modo Aluno. Não inserir ficheiros de fontes sem licença.
- Não colocar a prancha inteira como fundo, nem introduzir ilustrações distrativas no leitor. Botões grandes, alto contraste, labels, espaçamento e atenção ao TalkBack.
- Uma identidade consistente nas duas áreas, mas visualmente distintas pela função: **Aluno = leitura simples**; **Professor = criação/gestão de conteúdos**.

## Informação e navegação
### Ecrã de entrada
- Marca + nome **Ler sem Barreiras**, descrição curta e dois botões grandes: **Modo Aluno** e **Modo Professor**.
- Um acesso claro a **Sobre o projeto**.
- Não exigir login nem internet; preservar a navegação existente e o estado do utilizador.

### Modo Aluno
- Biblioteca de conteúdos com cartões simples e legíveis.
- Leitor com três modos já existentes: Texto completo, Palavra individual e Progressivo.
- Conserva o destaque a partir da posição real do Media3/ExoPlayer, controlos play/pausa/recomeçar, tamanho de fonte e alto contraste.
- Estilo calmo. Não usar publicidade, referências a patrocinadores, popups ou links institucionais durante a leitura.
- Adicionar entrada discreta para «Sobre o projeto» fora do leitor, p.ex. no menu da biblioteca.

### Modo Professor
- Lista e gestão de conteúdos; criar, editar, introduzir texto, gravar/importar áudio, sincronizar, rever e corrigir tempos, guardar.
- Explicar visualmente o estado de cada conteúdo (rascunho, sincronização, revisão, pronto) com rótulos e não só cores.
- Aplicar o sistema visual aos formulários, barras de progresso, cartões e mensagens; nunca fingir que uma operação foi concluída.
- Adicionar entrada de «Sobre o projeto» no menu principal do modo.
- Preservar dados Room, compatibilidade de ficheiros, permissões e regras de cancelamento de sync.

### Sobre o Projeto (NOVA página obrigatória)
Implementar um ecrã nativo Compose, totalmente visível offline, acessível desde a Home, Modo Aluno e Modo Professor.
Usar conteúdo editorial de `03_Especificacoes/SOBRE_O_PROJETO.md` e dados de `sobre_projeto_dados.json` (adaptar para resources/data layer Android).
A página deve ter:
1. Título, logótipo do projeto e resumo da missão.
2. **Origem / iniciativa:** Escola Gil Vicente / Agrupamento de Escolas Gil Vicente (instituição responsável e coordenação pedagógica).
3. **Para quem:** alunos com dificuldades de leitura e comunicação, e os seus professores.
4. **Como funciona:** texto + gravação + sincronização palavra-a-palavra, inteiramente offline.
5. **Desenvolvimento tecnológico:** HXB Informática e Tecnologia, colaboração voluntária e sem custo para a escola.
6. **Apoiantes e financiadores:** área data-driven (nome, tipo de apoio, logótipo local opcional, URL opcional), inicialmente SEM entidades ou com conteúdo aprovado pela escola. A correspondência refere Sonae, mas NÃO presumir entidade formal/branding/consentimento. Não inventar patrocínios nem logótipos.
7. **Código aberto:** aplicação gratuita para outras escolas e instituições, licença GPLv3, URL `https://github.com/HXB-Informatica/ler-sem-barreiras` (botão externo opcional, mas a página deve carregar offline).
8. **Versão da aplicação:** ler versionName e versionCode de forma dinâmica.
Não afirmar que a HXB é proprietária exclusiva nem que a escola detém exclusivamente direitos de autor de todas as contribuições. Explicar responsabilidades de projeto separadamente de direitos de autor.

## Recursos Android
- Integrar ícones `02_Android/res/mipmap-*`, `mipmap-anydpi-v26` e os recursos adequados, mas **reconciliar** com nomes/manifests já existentes; não apagar resource configs válidas.
- Tratar splash com APIs Android apropriadas e ícone, sem atrasos artificiais.
- Implementar cores e dimensões no tema Compose; documentar tokens e layouts.
- Usar `drawable-nodpi/lsb_symbol.png` apenas quando apropriado; não incorporar o icon master com fundo azul em cartões que exijam transparência.
- Deixar o branding institucional editável e não distribuir logos oficiais não fornecidos.

## Restrições técnicas inegociáveis
- **Manter o funcionamento sem internet**: nenhuma dependência de CDN, servidor, analytics, ads, login ou remote config.
- **Não mexer desnecessariamente no whisper.cpp, JNI, áudio, alinhamento, Media3, Room ou modelos**.
- A UI deve usar as funções existentes; não substituí-las por mockups.
- Preservar o Application ID, as compatibilidades e os conteúdos locais; evitar migrações destrutivas.
- Se um fluxo novo não existir, sinalizar claramente e não o mostrar como funcional.
- Não introduzir permissões novas sem motivo e sem explicar a necessidade.

## Execução e qualidade
1. Implementar interface Compose, navegação, tema, ícone e nova página Sobre.
2. Criar/ajustar testes de UI ou unitários para navegação e dados da página Sobre, sem estragar testes atuais.
3. Compilar e executar testes via Gradle. Usar o terminal e mostrar logs/resultados reais. Se houver emulador/tablet, validar visualmente Home, Aluno, Professor, Sobre, modo horizontal e vertical, com screenshots. Sem dispositivo, documentar explicitamente a lacuna.
4. Rever contraste, escalabilidade de texto e TalkBack, incluindo labels.
5. Atualizar README e documentação, com atribuições corretas à escola e HXB e licenças das imagens/recursos.
6. Se não existirem bloqueios, criar commit claro em branch adequada e publicar alterações segundo o fluxo Git já usado; **não publicar uma release automaticamente** nem alterar versão sem necessidade. Não sobrepor mudanças não commitadas.

## Relatório de saída obrigatório
- Resumo das alterações e lista de ecrãs afetados.
- Local dos novos recursos e ficheiros Compose.
- Capturas ou pré-visualizações disponíveis (com referência exata do que foi executado).
- Estado dos testes, build e eventuais erros.
- Confirmação de que Modo Aluno, Modo Professor e processamento offline se mantêm operacionais ou, se não houve teste real, especificar exatamente o que ficou por validar.
- Estado de Git (branch, commit, push, se efetuados) e próxima tarefa recomendada.

**Implementa de forma faseada, preserva o que já funciona e deixa o projeto operacional.**
