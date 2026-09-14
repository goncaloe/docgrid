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

**O que é versionado.** `AGENTS.md`, os procedimentos em `.agents/` e os invólucros das duas
ferramentas em uso (`.claude/skills/`, e `.agents/` para o oh-my-pi) estão no repositório: são
o método, e o método faz parte do que este projeto demonstra. Fora ficam os ficheiros das
ferramentas que não se usam aqui (`.cursor/`, `.codex/`, `GEMINI.md`) e a configuração de
máquina (`.pi/`) — esses são de quem desenvolve, não do produto.

## Os procedimentos deste projeto

Vivem em `.agents/prompts/` e estão escritos de forma neutra:

- `arrancar-etapa.md` — carrega o briefing da etapa, o handoff anterior e a documentação
  relevante, pede um plano e escreve-o em `docs/plans/` depois de aprovado.
- `implementar-etapa.md` — executa um plano já aprovado, com ou sem a conversa que o gerou.
- `handoff.md` — gera o relatório de fim de etapa em `docs/handoffs/`.

Se a tua ferramenta tiver comandos próprios, eles são invólucros finos destes ficheiros;
se não tiver, dizes-lhe simplesmente: *"segue `.agents/prompts/arrancar-etapa.md` para a
etapa 03"*. O resultado é o mesmo.

## Como abrir cada etapa

Três decisões — uma ou duas sessões, que modelo, que esforço de raciocínio — e seguem todas
os mesmos grupos de etapas. Por isso vivem numa tabela só: se a consultares para uma, já
tens as outras duas.

| Etapa | Caminho | Modelo · esforço | Porquê |
| --- | --- | --- | --- |
| 01, 03, 05, 11 (design difícil) | duas sessões | forte/alto a planear, rápido/médio a implementar | as decisões são caras de reverter; o plano é onde está o valor |
| 00, 02, 04, 06, 09, 10 (implementação normal) | duas sessões, se a etapa for grande | o rápido, médio | ganho sobretudo de contexto livre |
| 07, 08 (frontend, iteração visual) | uma sessão só | o rápido, médio | vês o ecrã e mudas de ideias; o plano envelhece em vinte minutos |
| 12 (escrita, README, diagramas) | uma sessão só | o mais capaz, médio | não há plano a executar, há texto a afinar |

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

Que etapas levam este caminho está na tabela de "Como abrir cada etapa", acima.

**O plano não é um documento morto.** Escreve-se antes de o código existir, portanto vai
deixar de bater certo nalgum ponto — isso não é falha do plano, é a condição normal. O que
interessa é o que acontece a seguir. Quem implementa escreve no ficheiro à medida que executa:
a secção "Desvios durante a execução" do `PLAN-TEMPLATE.md` leva uma linha por passo que não
saiu como estava escrito, commitada junto com o código desse passo.

Sem isso, o plano descreve o código só no minuto zero e depois mente cada vez mais, enquanto
os passos seguintes continuam a assumir que ele é verdade. E se a sessão se perder a meio,
quem retomar tem de reconstruir pelo diff onde é que ia. Registar custa trinta segundos por
desvio, e faz do plano a única coisa que é preciso ler para retomar — que é exatamente o
que ele devia ser.

A válvula de segurança é a regra do passo 3 de `implementar-etapa.md`: quando o plano não
chega, quem implementa **para e pergunta** em vez de decidir. Com o modelo forte fora da
sessão, essa regra deixa de ser boa educação e passa a ser estrutural — é o que faz o
problema voltar para ti em vez de ser resolvido mal e em silêncio.

É a peça mais valiosa do método, e pela razão menos óbvia: o modo de falhar que interessa
evitar não é a implementação bloquear à espera de ti, é o silêncio — uma decisão que ninguém
tomou a seguir para o commit porque ninguém sabe que ela existe. O raciocínio completo está
no passo 3 de `implementar-etapa.md`, que é quem precisa dele. Para ti chega a consequência:
**uma etapa que te fez duas perguntas correu melhor do que uma que não fez nenhuma.**

## Medir se o método está a funcionar

Tudo o que está acima é uma aposta: que separar o planeamento da execução produz código
melhor do que pedir tudo de uma vez. Convém não ficar pela fé. O projeto já produz os dados
para verificar — cada handoff regista os desvios ao plano, e nem todas as etapas correm da
mesma maneira.

Isso dá uma comparação natural, sem trabalho extra: as etapas que a tabela manda fazer em
duas sessões contra as que faz numa só (07, 08 e 12).

Não é um ensaio controlado — as etapas não têm a mesma dificuldade, e são poucas. Mas três
números por handoff chegam para ver uma tendência, e é o que a secção "Desvios ao plano" do
`HANDOFF-TEMPLATE.md` pede:

- **Detalhes de execução.** Quantos pontos o plano não previu mas que se resolveram sem
  decidir nada. Muitos não é mau sinal: é a fronteira normal entre o que se planeia e o que
  só se sabe com o ficheiro aberto.
- **Decisões que obrigaram a parar.** Zero é o número suspeito. Ou o plano estava mesmo
  completo, ou quem implementou adivinhou e não disse — e o diff é o único sítio onde se vê
  qual das duas foi.
- **Problemas só apanhados fora dos testes automatizados.** O melhor indicador isolado de
  qualidade do plano, porque mede o que nem o plano nem os testes que ele mandou escrever
  anteciparam. O handoff 08 tem três, todos do mesmo tipo — condições que nenhum teste
  simulava.

O que procurar ao fim de algumas etapas: se as de sessão única acumularem mais problemas
apanhados tarde, ou mais decisões silenciosas visíveis no diff, a separação está a pagar-se.
Se não houver diferença nenhuma, o ganho é só de contexto livre, e vale a pena reduzir a
cerimónia nas etapas pequenas em vez de a manter por hábito.

E há o sinal mais simples de todos, que não precisa de contagem: quando abres o handoff de
uma etapa antiga, consegues explicar as decisões que lá estão? Se sim, o método está a fazer
o que se pretendia dele.

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

O plano faz-se com o modelo mais capaz, a implementação com o rápido. Para o ciclo em duas
sessões:

```
Sessão A — modelo forte             Sessão B — modelo rápido
/model                              /model
/plan (ou Shift+Tab)                /implementar-etapa 03
/arrancar-etapa 03                  Shift+Tab → acceptEdits
  aprovas, sai do plan mode         npm test · /diff · /code-review
  escreve docs/plans/ + commit      /handoff 03
/clear                              /clear
```

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

## Apêndice C — pi / oh-my-pi

Ficheiro de instruções: `AGENTS.md`, lido nativamente, sem configuração. As skills do
projeto vivem em `.agents/skills/` — localização neutra que o pi descobre nativamente —
e são invólucros finos dos procedimentos em `.agents/prompts/`.

Comandos do projeto: `/skill:arrancar-etapa 03`, `/skill:implementar-etapa 03` e
`/skill:handoff 03`.

O pi tem modo de planeamento (plan mode), equivalente ao `/plan` do Claude Code: nada é
escrito em disco até o plano ser aprovado (alternar com `Alt+Shift+P`).

| Comando | Quando |
| --- | --- |
| `/model` | Trocar de modelo (ex.: modelos OpenRouter); Ctrl+S no seletor guarda o predefinido |
| `Shift+Tab` | Ciclar o esforço de raciocínio (equivalente a `/effort` do Claude); `--thinking` no arranque |
| `/compact` | Resumir o contexto a meio de uma etapa |
| `/new` | Sessão limpa: entre planear e implementar, e entre etapas depois do handoff |
| `/diff` | Rever o diff antes do commit (template em `.agents/commands/`, lido pelo oh-my-pi) |
| `/code-review` | Revisão crítica das alterações antes do commit |
| `/security-review` | Revisão de segurança nas etapas 02, 06 e 11 |
| `/reload-plugins` | Recarregar skills, comandos e prompts sem reiniciar |

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

Duas notas próprias do oh-my-pi. O `/reload-plugins` é preciso depois de mexeres numa skill ou num
procedimento — a sessão em curso não os relê sozinha. E se trocares de modelo com `/model`
sem abrir sessão nova, o contexto da discussão de planeamento vai com ele: é a variante de
uma sessão só, que serve as etapas 07, 08 e 12.
