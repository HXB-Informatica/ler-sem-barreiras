# Segurança

O Ler sem Barreiras trata de conteúdos educativos que podem incluir vozes e textos de crianças. A privacidade é um requisito de segurança.

## Modelo de ameaça desta versão

- A aplicação não tem permissão `INTERNET`.
- Os conteúdos ficam no armazenamento privado da aplicação.
- Não existem contas, nuvem, analytics nem SDK de publicidade.
- O motor de reconhecimento corre localmente.

Isto reduz a exposição remota, mas não elimina riscos locais (dispositivo partilhado, cópias de segurança do Android, *debug* USB, ou APK de depuração instalado em equipamentos não controlados).

## O que não deve ser enviado num relatório

- Gravações reais de alunos.
- Nomes, turmas, ou qualquer dado pessoal.
- Chaves de assinatura, *keystores* ou credenciais.

## Como comunicar uma vulnerabilidade

Utilize as **GitHub Security Advisories** deste repositório (separador *Security* → *Advisories* → *New draft security advisory*).

Não abra uma *issue* pública para falhas que possam expor dados de menores ou permitir acesso a conteúdos noutros dispositivos.

Os responsáveis farão o encaminhamento à Escola Gil Vicente e ao parceiro tecnológico HXB quando necessário. Não existe, nesta fase, um endereço de correio eletrónico institucional publicado para este efeito; o canal oficial é o GitHub.

## Versões suportadas

A versão 0.1.0 é a primeira versão pública de teste. Correções de segurança serão aplicadas sobre `main`.

## Boas práticas no tablet

- Usar o APK apenas em equipamentos da escola ou de teste.
- Não copiar a pasta de dados da aplicação para partilhas públicas.
- Remover conteúdos de teste antes de entregar o tablet a alunos, se tiverem sido criados com vozes de adultos para desenvolvimento.
