# Procedimento: fechar uma etapa (handoff)

Roteiro neutro, para qualquer agente de código. Quem te invoca indica **o número da etapa**
(duas casas: `00`, `03`, `11`). Onde este documento diz `NN`, usa esse número.

Escreve `docs/handoffs/STAGE-NN-handoff.md` seguindo `docs/HANDOFF-TEMPLATE.md`.

## Antes de escrever

1. Corre `git log --oneline` desde o início da etapa e `git diff --stat` para veres o que
   mudou de facto.
2. Corre a suite de testes e regista o resultado real. Se falhar alguma coisa, isso vai
   para o handoff — não escondas.
3. Confirma que os comandos de verificação que vais escrever funcionam mesmo. Um handoff
   com comandos que não correm é pior que nenhum.
4. Se houve plano, lê `docs/plans/STAGE-NN-plano.md` — a secção "Desvios durante a execução"
   e os pontos de paragem que se dispararam. É material já escrito; não o reinventes.

## O que o documento tem de conter

Segue o template. Sê específico onde ele pede especificidade: caminhos de ficheiros reais,
comandos que se copiam e colam, hashes de commits.

Duas secções merecem cuidado extra porque são as que salvam a próxima sessão:

**Decisões tomadas** — não só o que ficou decidido, mas o que foi posto de lado e porquê.
Sem isto, a sessão seguinte volta a discutir o mesmo.

**Armadilhas** — o conhecimento que só se obtém tendo trabalhado nisto: um comportamento
estranho do LocalStack, um workaround, uma limitação que descobriste a meio.

**Desvios ao plano** — se a etapa foi implementada a partir de `docs/plans/STAGE-NN-plano.md`,
começa pela secção "Desvios durante a execução" **do próprio plano**, que quem implementou foi
preenchendo, e acrescenta-lhe o que só apareceu depois: a verificação manual, a revisão do
diff, os testes. Não reconstituas a lista de memória a partir do `git log` se ela já existe —
é aí que os desvios pequenos desaparecem, e são esses que dizem se o plano estava bom.

Fecha com a linha de contagem que o template pede: quantos foram detalhes de execução, quantas
foram decisões que obrigaram a parar, quantos problemas só se apanharam fora dos testes
automatizados. São três números baratos de contar e é o que torna as etapas comparáveis
(`docs/05-WORKFLOW-AGENTES.md`, "Medir se o método está a funcionar").

## Depois de escrever

- Se houve decisões estruturantes, verifica se merecem um ADR em `docs/adr/`.
- Se a etapa mudou algo sempre-verdadeiro do projeto (um comando novo, uma alteração de stack),
  atualiza o `AGENTS.md`. Mantém-no curto. Não escrevas regras de projeto em ficheiros
  específicos de uma ferramenta — divergem em duas semanas.
- Faz commit da documentação: `docs(handoff): etapa NN`.
- Diz-me em duas linhas o que fica pronto para a etapa seguinte.
