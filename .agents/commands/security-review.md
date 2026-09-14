---
description: Revisão de segurança das alterações (etapas 02, 06 e 11)
argument-hint: "[área opcional, ex.: upload]"
---
Revisão de segurança das alterações desta sessão (`git status`, `git diff HEAD`),
nas áreas críticas deste projeto: upload de documentos, autenticação e
infraestrutura (ver `AGENTS.md` e `docs/02-ARCHITECTURE.md`).

Verifica, quando aplicável às alterações:

- **Segredos**: nada de credenciais em código ou repositório; tudo por variável de ambiente.
- **Upload/entrada de dados**: validação de tipo e tamanho, caminhos de ficheiro, conteúdo
  dos documentos submetidos.
- **Autenticação/autorização**: organização e papel validados em cada acesso; NIF e dados
  pessoais não expostos indevidamente.
- **SQL/Flyway**: queries parametrizadas, migrações sem SQL dinâmico com input.
- **Dependências novas**: questiona qualquer biblioteca fora da stack fixa.

Para cada achado, indica a severidade, o ficheiro e a linha, e a correção proposta.
Não edites nada; só analisa.

${1:+Área pedida: $1}
