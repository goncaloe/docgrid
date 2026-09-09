# Como conduzir as sessões com um agente de código

Este é o ficheiro mais importante do pacote. Lê-o antes da primeira sessão.

Nada aqui é específico de uma ferramenta: o método é o mesmo com Claude Code, Codex, Cursor
ou outro. As especificidades de cada uma estão nos apêndices, no fim.

## O ciclo de uma etapa

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Sessão limpa            contexto vazio, sem histórico    │
│ 2. Modo de planeamento     o agente lê, não escreve         │
│ 3. Carregar contexto       procedimento "arrancar etapa"    │
│ 4. Rever e aprovar o plano ← O TEU TRABALHO REAL            │
│ 5. Implementar             edições em lote, commits curtos  │
│ 6. Verificar               npm test · diff · revisão        │
│ 7. Commits                 pequenos, à medida               │
│ 8. Handoff                 procedimento "handoff"           │
│ 9. Fechar                  sessão limpa outra vez           │
└─────────────────────────────────────────────────────────────┘
```

O passo 4 é onde ganhas ou perdes. Se aprovares planos sem os ler, acabas com um repositório
que não sabes explicar — e numa entrevista isso vê-se em trinta segundos.

## Como o contexto está organizado

Um agente que abre este repositório precisa de saber as regras sem ler tudo. A organização
é deliberada:

| Camada | Onde vive | Quem a lê |
|---|---|---|
| Regras sempre verdadeiras | `AGENTS.md` | Todos os agentes, sempre |
| Especificidades da ferramenta | `CLAUDE.md` e afins | Só essa ferramenta |
| Procedimentos de tarefa | `.agents/prompts/*.md` | Quando a tarefa começa |
| Domínio, arquitetura, etapas | `docs/`, `stages/` | Sob demanda |

**`AGENTS.md` é a fonte única das regras.** Os ficheiros de ferramenta importam-no e contêm
só o que é próprio dela (comandos, modos, atalhos). Se puseres uma regra de projeto dentro
de um ficheiro de ferramenta, duas semanas depois tens duas versões da mesma regra a
contradizerem-se — e nunca sabes qual é que o agente leu.

Os ficheiros de agente não estão versionados (ver `.gitignore`): são configuração da máquina
de quem desenvolve, não do produto. O método, esse, está aqui e é versionado.

## Os dois procedimentos deste projeto

Vivem em `.agents/prompts/` e estão escritos de forma neutra:

- `arrancar-etapa.md` — carrega o briefing da etapa, o handoff anterior e a documentação
  relevante, e pede um plano.
- `handoff.md` — gera o relatório de fim de etapa em `docs/handoffs/`.

Se a tua ferramenta tiver comandos próprios, eles são invólucros finos destes ficheiros;
se não tiver, dizes-lhe simplesmente: *"segue `.agents/prompts/arrancar-etapa.md` para a
etapa 03"*. O resultado é o mesmo.

## Modelo e esforço

Quase todas as ferramentas deixam escolher entre um modelo mais forte e um mais rápido, e
algumas deixam ajustar o esforço de raciocínio. Sugestão para este projeto:

| Etapa | Modelo | Esforço |
|---|---|---|
| 01, 03, 05, 11 (design difícil) | o mais capaz disponível | alto |
| 00, 02, 04, 06, 09, 10 (implementação normal) | o rápido | médio |
| 07, 08 (frontend, muita iteração visual) | o rápido | médio |
| 12 (escrita, README, diagramas) | o mais capaz | médio |

Padrão que funciona bem: **planear com o modelo forte, implementar com o rápido.** Faz o
plano em modo de planeamento, aprova, troca de modelo e deixa executar. Poupa quota sem
perder qualidade nas decisões que contam.

Se só tiveres um modelo, fica com ele e sobe o esforço nas etapas de design.

## Gerir o contexto

- Vê o que ocupa a janela quando as respostas começarem a ficar vagas — é o primeiro sinal
  de saturação.
- Resumir a conversa a meio de uma etapa é preferível a limpá-la: mantém o fio.
- Limpar só entre etapas, depois do handoff escrito.
- Sabe como desfazer. Todas as ferramentas têm rede de segurança (um `rewind`, um
  checkpoint, ou no pior caso `git stash`). Descobre qual é a tua **antes** de precisares.

**Não deixes uma etapa arrastar-se por três dias na mesma sessão.** Se tiveres de parar a meio,
escreve o handoff parcial e fecha. Retomar com contexto fresco é sempre melhor que continuar
uma sessão exausta.

## Antes de cada commit

- **Vê o diff.** Lê mesmo. É a última barreira antes de código que não sabes explicar.
- **Revisão da alteração** — se a ferramenta tiver revisor integrado, usa-o; se não, pede
  explicitamente uma revisão crítica do que acabou de escrever.
- **Revisão de segurança** nas etapas 02, 06 e 11 (upload, autenticação, infraestrutura).
- `npm test` — não confies em "os testes devem passar".

## Anti-padrões

**"Aproveita e faz também a etapa seguinte."** É o erro mais comum. O contexto satura,
as decisões contradizem-se, e perdes a capacidade de rever o que foi feito.

**Aprovar planos em diagonal.** Se não percebes o plano, pede explicação antes de aprovar.
"Explica-me porque escolheste isto" é uma pergunta perfeitamente razoável e faz-te aprender.

**Deixar o handoff para depois.** Depois não te lembras. Escreve-o no fim da sessão,
enquanto o contexto ainda existe.

**Encher o `AGENTS.md`.** Deve manter-se curto e conter só factos sempre verdadeiros.
Procedimentos longos vão para `docs/` ou para `.agents/prompts/`, que só se carregam quando
são precisos.

**Espalhar regras por ficheiros de ferramenta.** Regra nova vai ao `AGENTS.md`. Sempre.

**Aceitar bibliotecas novas sem perguntar.** Se o plano introduzir uma dependência que não está
na stack, questiona. Metade das vezes não é precisa.

---

## Apêndice A — Claude Code

Ficheiro de instruções: `CLAUDE.md`, que importa o `AGENTS.md` com `@AGENTS.md`.
Comandos do projeto: `/arrancar-etapa 03` e `/handoff 03`, em `.claude/skills/`.

| Modo (Shift+Tab, ou `/plan`) | Quando usar |
|---|---|
| **plan** | Sempre no início de uma etapa. Lê e propõe, não escreve nada em disco. |
| **default** | Trabalho delicado: migrações, segurança, qualquer coisa que apague dados. |
| **acceptEdits** | Implementação de um plano já aprovado. Para de confirmares cada ficheiro. |
| **auto** | Só para tarefas repetitivas e reversíveis, com o git limpo antes de começares. |

Contexto: `/context` mostra o que ocupa a janela, `/compact` resume, `/clear` limpa,
`/rewind` (ou duplo Esc) desfaz código e conversa. Antes do commit: `/diff`, `/code-review`,
`/security-review`. Modelo e esforço: `/model` e `/effort` (de `low` a `xhigh`, mais `max`).

## Apêndice B — Codex, Cursor, Zed, Aider e outros

Estes leem o `AGENTS.md` da raiz nativamente, sem configuração. Não precisas de mais nada
para as regras do projeto ficarem ativas.

Sem comandos próprios, invoca os procedimentos por caminho:

```
Segue .agents/prompts/arrancar-etapa.md para a etapa 03.
```

Se um dia quiseres comandos nativos noutra ferramenta, cria o invólucro no formato dela
(`.gemini/commands/*.toml`, `.github/prompts/*.prompt.md`, uma regra `.cursor/rules/*.mdc`)
apontando para o mesmo ficheiro em `.agents/prompts/`. Nunca copies o procedimento para lá.
