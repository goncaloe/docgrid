# Etapa 03 — Pipeline assíncrono

## Objetivo
Um ficheiro que chega ao S3 dispara um evento, que vai para uma fila SQS, que é consumida
por um worker. Com idempotência, retries e dead-letter queue.

## Porque conta
**É a etapa mais valiosa do projeto para o portefólio.** Filas, entrega duplicada, DLQ e
idempotência são exatamente o vocabulário de uma entrevista para backend sénior. A maioria
dos projetos de portefólio não tem nada disto.

## Contexto a carregar
`docs/02-ARCHITECTURE.md` (integral), `docs/01-PRODUCT.md` (ciclo de vida),
handoff da etapa 02

## Pré-requisitos
Etapa 02: ficheiros chegam ao S3 e o documento existe em `UPLOADED`.

## Âmbito
- Notificação de eventos do S3 para SQS, configurada no LocalStack no arranque
- Fila principal + dead-letter queue, com `maxReceiveCount` definido
- Worker Spring que consome a fila (perfil `worker`, separado do perfil `api`)
- **Idempotência**: antes de processar, verificar se aquela chave S3 já foi processada.
  Mensagem repetida não cria trabalho duplicado.
- Transição `UPLOADED → PROCESSING`, e nesta etapa `PROCESSING → EXTRACTED` com dados falsos
  (a extração real é a etapa 04)
- Retries com backoff; após N tentativas a mensagem vai para a DLQ e o documento fica `FAILED`
- Timeout de visibilidade coerente com o tempo esperado de processamento
- Endpoint de administração para listar e reprocessar mensagens da DLQ
- Testes: entrega duplicada da mesma mensagem processa uma vez só; mensagem que falha
  sempre acaba na DLQ

## Fora
Extração real (etapa 04), regras de validação (etapa 05).

## Decisões desta etapa
- Como garantir idempotência: chave única na base de dados, tabela de mensagens processadas,
  ou verificação de estado. Discute as três, recomenda uma, escreve ADR.
- Worker como perfil da mesma aplicação vs módulo Maven separado
- O que é erro transitório (repetir) e o que é erro permanente (DLQ imediata)

## Critérios de aceitação
- [ ] Upload de um ficheiro faz o documento passar por `PROCESSING` sozinho, sem intervenção
- [ ] Enviar a mesma mensagem duas vezes à mão produz um único processamento (teste)
- [ ] Um documento que falha sempre acaba na DLQ e fica `FAILED` (teste)
- [ ] A DLQ é inspecionável e reprocessável
- [ ] Existe ADR sobre idempotência e sobre a escolha de SQS
- [ ] Logs incluem um id de correlação que atravessa API e worker

## Esforço estimado
1 a 2 sessões (fila + DLQ/idempotência) · exige plano detalhado
