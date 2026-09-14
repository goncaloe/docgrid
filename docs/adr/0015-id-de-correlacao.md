# 0015 — Id de correlação de ponta a ponta

**Estado:** aceite · **Data:** 2026-09-14

## Contexto

O pipeline é composto (upload → S3 → fila → worker → extração → validação), e até à
etapa 10 responder a "o que aconteceu ao documento 4821?" exigia abrir a base de dados.
Para os logs contarem a história de um pedido é preciso um id de correlação que atravesse
toda a linha — com uma particularidade do desenho que decide tudo: **quem produz a
mensagem que o worker consome é o S3**, não a nossa API (a notificação `ObjectCreated`
S3→SQS). Um evento do S3 não carrega atributos nossos.

## Decisão

A correlação propaga-se por **dois caminhos**, porque há dois produtores:

1. **Pela base de dados** — o caminho normal. O upload grava
   `documents.correlation_id`; o worker, ao processar, lê-o do documento e restaura-o no
   MDC. É o elo entre os logs da API e os do worker quando a mensagem veio do S3.
2. **Pelo atributo de mensagem SQS** (`X-Correlation-Id`) — o que a aplicação envia
   (o redrive da DLQ). O `SqsQueues.sendToMain` copia o id do MDC para o atributo; o
   worker, ao receber, devolve-o ao MDC.

O id é um **UUID canónico** gerado no filtro HTTP; um header do cliente aceita-se
**saneado** (máx. 64 caracteres, `[A-Za-z0-9_-]`) — um valor que não passa é substituído
por um UUID novo. Saneamento, e não recusa: um header com lixo não derruba o pedido, e
não entra nos logs.

O `documentId` continua no MDC **ao lado** do `correlationId`. São coisas diferentes:
o `documentId` liga tudo o que aconteceu a um documento ao longo de dias; o
`correlationId` liga o que aconteceu num pedido. O critério de aceitação nº 1 da etapa
é sobre o primeiro.

## Alternativas consideradas

- **Só o atributo SQS** (como o briefing sugeria): o produtor do caminho normal é o S3,
  e um atributo nosso nunca lá estaria — perder-se-ia o elo do caminho que interessa.
- **Só a coluna**: perdia-se o rasto do redrive, que é uma ação humana que vale a pena
  seguir.
- **Aceitar o header como vem** ou **ULID/hex próprio**: o UUID é o formato de id do
  resto do projeto; aceitar lixo in natura arriscaria injeção de texto nos logs e
  cardinalidade infinita.
- **Substituir o `documentId` pelo `correlationId`**: são id de natureza diferente; um
  não substitui o outro.

## Consequências

- Um `correlation_id` nulo num documento anterior à etapa 10 é normal, não é erro: o
  MDC fica sem a chave e o padrão imprime `cid=` vazio. O worker não inventa um id novo
  para o substituir — isso criaria um rasto falso.
- O `Correlation` vive no MDC (`MDC_KEY = "correlationId"`), onde o padrão de log
  (`%X{correlationId:-}`) e o formatador ECS o leem como campo de topo.
- **Tracing distribuído (OpenTelemetry / Micrometer Tracing)** fica de fora: num sistema
  com dois processos (API e worker) o esforço não se paga. É a evolução natural se o
  sistema crescer.
