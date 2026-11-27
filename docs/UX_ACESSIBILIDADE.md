# UX e acessibilidade (v0.2)

Decisões aplicadas na app React Native, sem alterar o motor de sincronização.

## Aluno

- Biblioteca com cartões grandes; o vazio fala com a criança, não com o professor.
- Sem referências a `.lsb`, sincronização ou importação.
- Destaque da palavra com cor, negrito e sublinhado (não só cor).
- Controlos com pelo menos 48 dp e etiquetas TalkBack.

## Professor

- Criação em quatro etapas (texto, narração, sincronizar, guardar).
- **Guardar rascunho** define `availableToStudents: false`.
- **Disponibilizar para leitura** exige texto, áudio e sincronização `COMPLETED`, sem palavras por rever.
- `COMPLETED_WITH_GAPS`, falhas e rascunhos (`availableToStudents: false`) não entram na biblioteca do aluno.
- Conteúdos antigos sem o campo novo continuam visíveis só se já tiverem áudio e sincronização `COMPLETED` sem palavras por rever.
- No cabeçalho, o espaço da direita só mostra o botão «Sobre» quando existe; não se desenha um círculo vazio.
- As quatro etapas do professor cabem numa única linha em ecrãs estreitos.
- Aviso ao sair com alterações por guardar.

## Layout

- Largura máxima em tablets; duas colunas na biblioteca do aluno em ecrãs largos.
- No professor, narração e controlos lado a lado em horizontal no tablet.
- No ecrã inicial em horizontal, logótipo e botões ficam lado a lado; os botões não ultrapassam 480 dp.

## O que não mudou

Whisper/EBranch, Media3/bridge de áudio, formato `.lsb`, Room/ficheiros locais, GPLv3, funcionamento offline.
