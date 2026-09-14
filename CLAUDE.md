@AGENTS.md

---

# Específico do Claude Code

As regras do projeto estão todas no `AGENTS.md` importado acima. Aqui fica só o que
não se aplica a outros agentes.

## Skills deste projeto

- `/arrancar-etapa NN` — abre a etapa NN: carrega briefing, handoff anterior e docs, propõe
  plano e escreve-o em `docs/plans/` depois de aprovado.
- `/implementar-etapa NN` — executa o plano de `docs/plans/STAGE-NN-plano.md` numa sessão limpa.
- `/handoff NN` — fecha a etapa NN: escreve o relatório em `docs/handoffs/`.

As três são invólucros finos em `.claude/skills/`, que apontam diretamente para o procedimento
em `.agents/prompts/` — a fonte única, partilhada com o oh-my-pi. Se corrigires um
procedimento, corrige-o lá; os invólucros não têm conteúdo próprio.

## O resto está no manual

Modos (Shift+Tab), comandos de contexto e de revisão, escolha de modelo e esforço, e o ciclo
em duas sessões: `docs/05-WORKFLOW-AGENTES.md`, **apêndice A**. Não repitas aqui nada disso —
é o anti-padrão "espalhar regras por ficheiros de ferramenta" do `AGENTS.md`, e a versão que
fica desatualizada é sempre esta.
