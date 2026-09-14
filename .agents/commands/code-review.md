---
description: Revisão crítica das alterações da sessão vs HEAD
argument-hint: "[foco opcional]"
---
Revisão crítica de tudo o que esta sessão alterou. Corre `git status` e `git diff HEAD`
e avalia o conjunto como um revisor independente:

- **Conformidade com o plano aprovado** — desvios não anunciados são o problema mais grave;
  se a implementação divergiu do plano, diz onde e porquê.
- **Correção e robustez**: casos limite, erros propagados, estados intermédios.
- **Convenções**: `docs/03-CONVENTIONS.md` (código, testes, commits) e regras do `AGENTS.md`
  — confiança explícita nos campos extraídos, inglês no código, português nos commits.
- **Âmbito**: há aqui algo que pertence a uma etapa futura? (anti-padrão clássico.)

Aponta cada problema com ficheiro e linha, e diz qual a correção que propões.
Não edites nada; só analisa.

${1:+Foco pedido: $1}
