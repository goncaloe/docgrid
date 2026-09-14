# Procedimento: implementar uma etapa a partir de um plano

Roteiro neutro, para qualquer agente de código. Quem te invoca indica **o número da etapa**
(duas casas: `00`, `03`, `11`). Onde este documento diz `NN`, usa esse número.

Existe um plano aprovado em `docs/plans/STAGE-NN-plano.md`. Foi escrito noutra sessão,
possivelmente por um modelo mais capaz do que o que está a ler isto. Não assististe à
discussão que o produziu: **o que não estiver no ficheiro, não existe.** Isso não é convite
a preencheres as lacunas por tua conta — é o contrário. Ver o passo 3.

O plano é a fonte de verdade, mas não é só de leitura: **escreves nele à medida que o
executas.** Foi feito antes de o código existir e vai deixar de bater certo nalgum ponto;
quando isso acontecer, o desvio fica registado no ficheiro, não só na tua cabeça e não só
no handoff do fim. Ver o passo 2.

## 1. Carregar contexto

Lê, por esta ordem:

1. `docs/plans/STAGE-NN-plano.md` — o plano aprovado. É a fonte de verdade do **como**.
2. `stages/STAGE-NN-*.md` — o briefing da etapa. É a fonte de verdade do **âmbito**.
   Se o plano e o briefing discordarem, o briefing ganha e tu paras para dizer.
3. O handoff mais recente em `docs/handoffs/` — o que existe já no código.
4. O estado real do disco. Os ficheiros que o plano diz que vai alterar existem mesmo?
   Confirma antes de começar, não a meio.

Não leias toda a pasta `docs/`. O plano traz as decisões já tomadas; se precisares de um
documento que ele não cita para perceberes o que fazer, isso é uma lacuna do plano — passo 3.

## 2. Implementar

Segue os passos do plano pela ordem em que estão. Cada passo é uma unidade de commit:
implementas, escreves os testes que o plano lhe atribui, corre-los, commitas. Não acumules
três passos num commit só nem deixes os testes para o fim.

**Quando um passo não sair como está escrito, atualiza o plano antes de commitar esse passo.**
Uma linha na secção "Desvios durante a execução" de `docs/plans/STAGE-NN-plano.md`: que passo,
o que o plano dizia, o que ficou, e se foi detalhe de execução ou decisão (a distinção está no
passo 3). O commit da documentação vai junto com o do código do passo.

Custa trinta segundos e resolve três coisas. O plano continua a descrever o que existe, para
os passos seguintes que dependem dele. Se a sessão se perder a meio, quem retomar lê onde
estavas em vez de reconstruir pelo diff. E o handoff do fim consolida uma lista já escrita,
em vez de a reconstituir de memória — que é quando os desvios pequenos desaparecem.

Não reescrevas os passos que ainda não executaste para os fazer bater certo com a tua ideia
nova: isso é decidir em silêncio com outro nome. Registas o desvio do passo que fizeste; se
ele invalida um passo seguinte, é caso de parar e perguntar.

Convenções de código, de testes e de commits: `docs/03-CONVENTIONS.md`.

## 3. Quando o plano não chega

O plano foi escrito antes de o código existir. Vai haver pontos onde não encaixa. A distinção
que interessa é esta:

**Detalhe de execução** — um nome de método, um import, uma assinatura diferente do que o
plano supunha, um caso de teste a mais. Resolves, e registas no plano (passo 2).

**Decisão** — o que muda o desenho, acrescenta uma dependência, altera um contrato, ou que
só resolverias por palpite. **Para e diz.** Não escolhas em silêncio: quem escreveu o plano
tinha contexto que tu não tens, e a decisão é para ser tomada lá.

Esta é a regra mais importante deste ficheiro, e vale a pena perceber porquê. O pior resultado
possível não é a implementação bloquear à espera de uma resposta — é o contrário: preencheres
a lacuna por palpite, acertar parcialmente, e a decisão errada seguir para o commit sem nunca
ter sido tomada por ninguém. Ninguém a revê, porque ninguém sabe que existe. Aparece três
etapas à frente como um comportamento que ninguém consegue explicar. Bloquear é barato e
visível; adivinhar é barato **agora** e caro depois.

Antes de parares, escreve a pergunta em "Riscos e pontos de paragem", no plano. Se a conversa
se perder, a pergunta não se perde com ela.

Sinais de que é para parar:

- o plano manda usar uma classe, tabela ou endpoint que não existe no disco
- duas partes do plano contradizem-se
- o plano deixou um ponto explicitamente marcado como "decidir aqui"
- estás a implementar alguma coisa que o plano não menciona
- estás a implementar alguma coisa que o briefing atribui a outra etapa

## 4. Fechar

- Corre a suite de testes de facto e lê o resultado. Não escrevas "devem passar".
- Revê o diff completo antes do último commit.
- Relê a secção **"Desvios durante a execução"** do plano. Se foste registando (passo 2), a
  lista já está escrita e só falta ver se ficou alguma coisa de fora — em particular o que
  apareceu na verificação manual, depois do último passo. Se está vazia e o diff não bate
  certo com o plano, não foi o plano que estava perfeito: foi o registo que não se fez.
- O handoff, a seguir, consolida essa lista com `.agents/prompts/handoff.md`.
