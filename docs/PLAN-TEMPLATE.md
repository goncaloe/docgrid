# Plano — Etapa NN: <nome>

**Data:** AAAA-MM-DD · **Planeado com:** <modelo> · **Estado:** aprovado | em revisão

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.
>
> E não é um documento morto: quem implementa escreve nele quando ele deixar de bater certo
> com o disco — ver "Desvios durante a execução", no fim.

## Contexto
- <o que já existe e esta etapa vai usar, e o que mudou desde o handoff anterior>

## O que se vai construir
- <3 a 5 pontos>

## Pré-requisitos verificados
- <o que a etapa assume que já existe, e onde foi confirmado no disco>

## Decisões tomadas
| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| | | |

ADRs a escrever: `docs/adr/NNNN-*.md`

## Riscos e pontos de paragem
O que pode correr mal, e o que fazer quando correr. Tudo o que obriga a parar vive aqui —
tanto o que já se sabe à partida que precisa de decisão, como a condição que só se descobre
a meio. Quem implementa lê isto **antes** do primeiro passo, e acrescenta-lhe as perguntas
novas antes de parar.

- <risco ou ponto aberto> → **para e pergunta** se <condição>, em vez de <o palpite óbvio>

## Passos de implementação
Por ordem. Cada passo é uma unidade de commit.

1. **<título>** — `caminho/Ficheiro.java` (criar | alterar)
   - <o que faz>
   - Testes: `caminho/FicheiroTest.java` — <o que cobre>
   - Commit: `feat(...): ...`

## Critérios de aceitação
Comandos exatos que provam que a etapa está feita:
```bash
npm test
curl ...
```
Resultado esperado: <...>

## Fora de âmbito
- <item> → pertence à etapa NN

## Desvios durante a execução
Preenchido por **quem implementa, à medida que acontece** — não no fim, não no handoff.
Quando o plano deixar de bater certo com o disco, corrige-se aqui e o problema fica visível
para quem retomar.

| Passo | O que o plano dizia | O que ficou | Detalhe ou decisão |
|---|---|---|---|
| | | | |
