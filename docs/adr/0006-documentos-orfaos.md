# 0006 — Documentos órfãos: estratégia sem limpeza automática

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

O registo do documento é criado em `UPLOADED` no momento em que se emite a autorização de
upload (ADR 0005). Se o cliente nunca fizer o `PUT`, fica um documento sem ficheiro — um
órfão. O briefing da etapa 02 pede a estratégia e diz para "implementar a limpeza só se
for barata".

Há dois tipos de órfão:

- **Sem objeto no S3.** O cliente pediu o URL e nunca o usou. Custo: uma linha na base de
  dados, mais nada. As filas de trabalho (revisão, aprovação) filtram por estado, portanto
  o órfão nunca aparece a ninguém. É inerte.
- **Com objeto no S3, nunca referido.** O cliente fez o `PUT` mas — antes da etapa 03 —
  nada mais acontece. Custo: armazenamento no S3.

## Decisão

**Nesta etapa, nada de código de limpeza.** Só o desenho.

Uma limpeza a sério não é barata: exige uma transição nova na máquina de estados
(`UPLOADED → REJECTED`, que a etapa 01 não previu) e uma reconciliação com o S3 para
distinguir os dois tipos de órfão. Mexer na máquina de estados por causa disto seria
alargar o âmbito da etapa 02 ao domínio que a etapa 01 fechou.

Para produção, a recomendação é uma **regra de ciclo de vida do S3** sob o prefixo
`org/` que expira objetos ao fim de N dias (por exemplo 7). Apanha o segundo tipo de
órfão sem uma linha de código de aplicação, e escreve-se no Terraform da etapa 11, onde o
bucket é definido. O primeiro tipo continua inerte; se algum dia o volume de linhas órfãs
justificar, acrescenta-se então a transição e um trabalho agendado que as feche — com o
seu próprio ADR.

A etapa 03, ao ligar o evento do S3 ao worker, faz a maior parte do problema desaparecer:
um `PUT` bem-sucedido passa a disparar o processamento sozinho, portanto a janela em que
um documento com ficheiro fica "esquecido" fecha-se.

## Alternativas consideradas

**Trabalho `@Scheduled` que fecha órfãos ao fim de 24 h.** Exigia a transição
`UPLOADED → REJECTED` (três ficheiros: o enum, o teste da matriz, `docs/01-PRODUCT.md`) e
a distinção dos dois tipos de órfão contra o S3. Não é "barato" no sentido do briefing, e
antecipa uma decisão de domínio sem necessidade atual.

**Apagar a linha do órfão.** Contraria a decisão da etapa 01 de nunca apagar documentos.
Um órfão sem eventos e sem ficheiro é discutível como "documento", mas a exceção abre a
porta a outras.

**Não criar o registo até o upload terminar.** Contraria a decisão de arquitetura: "nada
sobe sem rasto". O registo em `UPLOADED` no momento da autorização é o rasto.

## Consequências

**Torna fácil:** a etapa 02 não toca na máquina de estados; o problema resolve-se onde é
natural resolvê-lo (regra do S3, na etapa 11; automação do disparo, na etapa 03).

**Torna difícil:** até à etapa 11, um ambiente que acumule uploads iniciados e nunca
concluídos acumula objetos no S3 sem limpeza. Em desenvolvimento local é irrelevante; em
qualquer ambiente sério, a regra de ciclo de vida tem de entrar com o bucket.
