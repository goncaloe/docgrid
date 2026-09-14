---
description: Rever o diff atual antes do commit
argument-hint: "[foco opcional]"
---
Rever as alterações ainda por commit. Corre `git status` e `git diff` (mais
`git diff --cached` se houver coisas em staging) e apresenta:

- **O que mudou**, em 3 a 5 pontos, em português.
- **Problemas**: bugs, lógica errada, código morto, nomes pouco claros.
- **Regras do projeto**: verifica a conformidade com o `AGENTS.md` (regras permanentes)
  e, para backend/frontend, com `docs/03-CONVENTIONS.md`.
- **Testes**: falta algum teste para o comportamento novo ou alterado?

Sê crítico e concreto — aponta ficheiro e linha. Não edites nada; só analisa.

${1:+Foco pedido: $1}
