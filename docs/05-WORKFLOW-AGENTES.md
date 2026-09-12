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
│ 5. Escrever o plano        docs/plans/STAGE-NN-plano.md     │
│ 6. Implementar             procedimento "implementar etapa" │
│ 7. Verificar               npm test · diff · revisão        │
│ 8. Commits                 pequenos, à medida               │
│ 9. Handoff                 procedimento "handoff"           │
│ 10. Fechar                 sessão limpa outra vez           │
└─────────────────────────────────────────────────────────────┘
```

O passo 4 é onde ganhas ou perdes. Se aprovares planos sem os ler, acabas com um repositório
que não sabes explicar — e numa entrevista isso vê-se em trinta segundos.

O corte natural é entre o 5 e o 6: podes fechar a sessão com o plano commitado e implementar
noutra, com um modelo mais barato. Ver "Planear e implementar em sessões separadas".

## Como o contexto está organizado

Um agente que abre este repositório precisa de saber as regras sem ler tudo. A organização
é deliberada:

| Camada | Onde vive | Quem a lê |
| --- | --- | --- |
| Regras sempre verdadeiras | `AGENTS.md` | Todos os agentes, sempre |
| Especificidades da ferramenta | `CLAUDE.md` e afins | Só essa ferramenta |
| Procedimentos de tarefa | `.agents/prompts/*.md` | Quando a tarefa começa |
| Domínio, arquitetura, etapas | `docs/`, `stages/` | Sob demanda |
| Plano da etapa em curso | `docs/plans/STAGE-NN-plano.md` | Quem implementa a etapa |

**`AGENTS.md` é a fonte única das regras.** Os ficheiros de ferramenta importam-no e contêm
só o que é próprio dela (comandos, modos, atalhos). Se puseres uma regra de projeto dentro
de um ficheiro de ferramenta, duas semanas depois tens duas versões da mesma regra a
contradizerem-se — e nunca sabes qual é que o agente leu.

Os ficheiros de agente não estão versionados (ver `.gitignore`): são configuração da máquina
de quem desenvolve, não do produto. O método, esse, está aqui e é versionado.

## Os procedimentos deste projeto

Vivem em `.agents/prompts/` e estão escritos de forma neutra:

- `arrancar-etapa.md` — carrega o briefing da etapa, o handoff anterior e a documentação
  relevante, pede um plano e escreve-o em `docs/plans/` depois de aprovado.
- `implementar-etapa.md` — executa um plano já aprovado, com ou sem a conversa que o gerou.
- `handoff.md` — gera o relatório de fim de etapa em `docs/handoffs/`.

Se a tua ferramenta tiver comandos próprios, eles são invólucros finos destes ficheiros;
se não tiver, dizes-lhe simplesmente: *"segue `.agents/prompts/arrancar-etapa.md` para a
etapa 03"*. O resultado é o mesmo.

## Modelo e esforço

Quase todas as ferramentas deixam escolher entre um modelo mais forte e um mais rápido, e
algumas deixam ajustar o esforço de raciocínio. Sugestão para este projeto:

| Etapa | Modelo | Esforço |
| --- | --- | --- |
| 01, 03, 05, 11 (design difícil) | o mais capaz disponível | alto |
| 00, 02, 04, 06, 09, 10 (implementação normal) | o rápido | médio |
| 07, 08 (frontend, muita iteração visual) | o rápido | médio |
| 12 (escrita, README, diagramas) | o mais capaz | médio |

Padrão que funciona bem: **planear com o modelo forte, implementar com o rápido.** Faz o
plano em modo de planeamento, aprova, troca de modelo e deixa executar. Poupa quota sem
perder qualidade nas decisões que contam.

Se só tiveres um modelo, fica com ele e sobe o esforço nas etapas de design.

## Planear e implementar em sessões separadas

A variante mais forte do padrão acima: em vez de trocares de modelo a meio da sessão,
**escreves o plano em disco e fechas a sessão.** A implementação arranca limpa, com o
modelo barato, a ler o ficheiro.

```
Sessão A — modelo forte          Sessão B — modelo rápido
/arrancar-etapa 03               /implementar-etapa 03
  plano discutido e aprovado       lê docs/plans/STAGE-03-plano.md
  escrito em docs/plans/           implementa, commits pequenos
  commit + fecha                   npm test · diff · revisão
                                   /handoff 03
```

**O que se ganha.** A janela inteira para implementar, em vez de a partilhar com uma
discussão de design que já cumpriu o seu papel. Um plano que se revê com calma, em ficheiro,
em vez de o aprovares a correr o histórico da conversa. E retoma barata: se a implementação
descarrilar, voltas ao plano, não ao princípio.

**O que se perde, e é preciso compensar.** A sessão B não herda nada: nem os ficheiros lidos,
nem as alternativas discutidas, nem os "isso pertence à etapa 09". O que não estiver escrito
no plano desapareceu. Um plano de bullets vagos entregue a um modelo barato produz
exatamente o que se teme — ele preenche as lacunas por palpite, e só se vê no diff.
Por isso `docs/PLAN-TEMPLATE.md` insiste em caminhos exatos, alternativas rejeitadas,
critérios de aceitação e pontos explicitamente marcados como "para e pergunta".

**Quando usar cada um:**

| Etapa | Caminho | Porquê |
| --- | --- | --- |
| 01, 03, 05, 11 (design difícil) | duas sessões | as decisões são caras de reverter; o plano é onde está o valor |
| 00, 02, 04, 06, 09, 10 | duas sessões, se a etapa for grande | ganho sobretudo de contexto livre |
| 07, 08 (iteração visual) | uma sessão só | vês o ecrã e mudas de ideias; o plano envelhece em vinte minutos |
| 12 (escrita) | uma sessão só | não há plano a executar, há texto a afinar |

A válvula de segurança é a regra do passo 3 de `implementar-etapa.md`: quando o plano não
chega, quem implementa **para e pergunta** em vez de decidir. Com o modelo forte fora da
sessão, essa regra deixa de ser boa educação e passa a ser estrutural — é o que faz o
problema voltar para ti em vez de ser resolvido mal e em silêncio.

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
Comandos do projeto, em `.claude/skills/`: `/arrancar-etapa 03`, `/implementar-etapa 03`
e `/handoff 03`.

| Modo (Shift+Tab, ou `/plan`) | Quando usar |
| --- | --- |
| **plan** | Sempre no início de uma etapa. Lê e propõe, não escreve nada em disco. |
| **default** | Trabalho delicado: migrações, segurança, qualquer coisa que apague dados. |
| **acceptEdits** | Implementação de um plano já aprovado. Para de confirmares cada ficheiro. |
| **auto** | Só para tarefas repetitivas e reversíveis, com o git limpo antes de começares. |

Contexto: `/context` mostra o que ocupa a janela, `/compact` resume, `/clear` limpa,
`/rewind` (ou duplo Esc) desfaz código e conversa. Antes do commit: `/diff`, `/code-review`,
`/security-review`. Modelo e esforço: `/model` e `/effort` (de `low` a `xhigh`, mais `max`).

Para o ciclo em duas sessões: `/arrancar-etapa` corre em plan mode e sai dele só para
escrever o plano; a seguir, `/clear`, `/model` para o modelo barato, `/implementar-etapa`.

## Apêndice B — Codex, Cursor, Zed, Aider e outros

Estes leem o `AGENTS.md` da raiz nativamente, sem configuração. Não precisas de mais nada
para as regras do projeto ficarem ativas.

Sem comandos próprios, invoca os procedimentos por caminho:

```
Segue .agents/prompts/arrancar-etapa.md para a etapa 03.
Segue .agents/prompts/implementar-etapa.md para a etapa 03.
```

Se um dia quiseres comandos nativos noutra ferramenta, cria o invólucro no formato dela
(`.gemini/commands/*.toml`, `.github/prompts/*.prompt.md`, uma regra `.cursor/rules/*.mdc`)
apontando para o mesmo ficheiro em `.agents/prompts/`. Nunca copies o procedimento para lá.

## Apêndice C — pi

Ficheiro de instruções: `AGENTS.md`, lido nativamente, sem configuração. As skills do
projeto vivem em `.agents/skills/` — localização neutra que o pi descobre nativamente —
e são invólucros finos dos procedimentos em `.agents/prompts/`.

Comandos do projeto: `/skill:arrancar-etapa 03`, `/skill:implementar-etapa 03` e
`/skill:handoff 03`.

O pi tem modo de planeamento (plan mode), equivalente ao `/plan` do Claude Code: nada é
escrito em disco até o plano ser aprovado. Não há ciclo Shift+Tab de modos.

| Comando | Quando |
| --- | --- |
| `/model` | Trocar de modelo (ex.: modelos OpenRouter); Ctrl+S no seletor guarda o predefinido |
| `/thinking` | Ajustar o esforço de raciocínio (equivalente a `/effort` do Claude) |
| `/compact` | Resumir o contexto a meio de uma etapa |
| `/new` | Sessão limpa: entre planear e implementar, e entre etapas depois do handoff |
| `/diff` | Rever o diff antes do commit (template em `.pi/prompts/`) |
| `/code-review` | Revisão crítica das alterações antes do commit |
| `/security-review` | Revisão de segurança nas etapas 02, 06 e 11 |
| `/reload` | Recarregar skills e prompts sem reiniciar |

O plano faz-se com o modelo mais capaz, a implementação com o rápido. Para o ciclo em duas
sessões:

```
Sessão A — modelo forte             Sessão B — modelo rápido
/model                              /model
plan mode                           /skill:implementar-etapa 03
/skill:arrancar-etapa 03            /diff · /code-review
  aprovas, sai do plan mode         /skill:handoff 03
  escreve docs/plans/ + commit      /new
/new
```

Duas notas próprias do pi. O `/reload` é preciso depois de mexeres numa skill ou num
procedimento — a sessão em curso não os relê sozinha. E se trocares de modelo com `/model`
sem abrir sessão nova, o contexto da discussão de planeamento vai com ele: é a variante de
uma sessão só, que serve as etapas 07, 08 e 12.
