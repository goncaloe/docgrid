# Handoff — Etapa 03: Pipeline assíncrono

**Data:** 2026-09-09 · **Sessão:** #4 (parte por outro agente, concluída nesta) · **Estado:** completa

## Contexto de arranque

A etapa tinha sido começada por outro agente e ficou a meio: a lógica de domínio estava
*committed* mas o transporte SQS **não compilava**, não havia testes nenhuns, nem ADRs,
nem endpoint de DLQ, e o worker não arrancava em local. Esta sessão fechou tudo isso.

## O que ficou feito

### Transporte da fila — `com.docgrid.pipeline` (novo pacote)

- **`SqsConfig`** — o `SqsClient`, montado a partir de `QueueProperties` com o mesmo
  desenho de `StorageConfig`: `endpoint` presente ⇒ LocalStack com credenciais estáticas;
  vazio ⇒ AWS com a cadeia por omissão. Bean em todos os perfis (o worker consome, a API
  administra a DLQ).
- **`QueueProperties`** (`@ConfigurationProperties("docgrid.queue")`) — `name`, `dlqName`,
  `region`, `endpoint`, credenciais, `maxReceiveCount` (3), `waitTime` (10s). O
  `visibilityTimeout` **não** está aqui de propósito: é atributo da fila, criado pela
  infraestrutura.
- **`SqsQueues`** — receber / apagar / devolver mensagens da fila principal e da DLQ. URLs
  resolvidos preguiçosamente e em cache. Recebe com `MessageSystemAttributeName.ALL` — é o
  `ApproximateReceiveCount` que diz ao worker se é a última entrega antes da DLQ.
- **`S3EventNotificationParser`** + `S3ObjectReference` + `MalformedS3EventException` — lê
  o evento do S3 à mão (o SDK v2 não traz leitor). Trata o `s3:TestEvent` do arranque
  (ignora), a chave url-encoded (descodifica), e recusa o que não for legível com
  `MalformedS3EventException extends DomainException` — erro permanente, a mensagem segue
  para a DLQ.
- **`DocumentWorker`** (`@Profile("worker")`, `SmartLifecycle`) — long polling num fio
  próprio. O passo receber+tratar+ack está em `drainOnce()` (package-private) para os
  testes o correrem sem o fio. Apagar a mensagem só acontece quando tudo correu bem; caso
  contrário volta à fila e esgota as entregas até à DLQ.
  - **Correção crítica:** o código do outro agente usava `QueueAttributeName.APPROXIMATE_RECEIVE_COUNT`,
    que não existe no SDK v2 — é `MessageSystemAttributeName`. Não compilava.

### Processamento — `com.docgrid.document` (do outro agente, + acréscimo)

- `DocumentProcessor` — reclama o documento idempotentemente (`ProcessingClaim`, chave do
  S3 como PK), lê o objeto, extrai com o `StubExtractor`, escreve tamanho + hash SHA-256 +
  campos com confiança + projeção, e transita `UPLOADED → PROCESSING → EXTRACTED`. Três
  transações explícitas (`TransactionTemplate`): reclamar / trabalhar / escrever.
- **`DocumentProcessor.reopenForReprocessing(storageKey)`** (novo) — o reprocessamento
  manual do ciclo de vida (`FAILED → PROCESSING`): passa o documento a `PROCESSING` e
  reabre o claim (`ProcessingClaim.reopen()`, `completed_at` volta a nulo) para a entrega
  seguinte retomar em vez de tratar como duplicado.

### Administração da DLQ — `com.docgrid.pipeline`

- **`DlqAdmin`** + **`DlqAdminController`**:
  - `GET /api/admin/dlq` — espreita as mensagens (visibilidade 0, não consome). Para
    eventos legíveis mostra bucket + chave; para os ilegíveis, um preview do corpo.
  - `POST /api/admin/dlq/redrive` — devolve tudo à fila principal. **Reprocessar é
    reprocessar mesmo:** cada documento `FAILED` que uma mensagem refira é reaberto antes
    de a mensagem voltar. Copia o corpo para a fila e só então apaga da DLQ.
- Sem autenticação — `/api/admin/**` fica atrás de um papel na etapa 06.

### Correlação de logs (etapa 10 fá-lo a sério)

- `documentId` no MDC no `DocumentUploadService` (API) e no `DocumentProcessor` (worker).
- `logging.pattern.level` em `application.yml`: `%5p [doc=%X{documentId:-} msg=%X{messageId:-}]`.
  `msg` é o id da mensagem SQS, só do lado do worker.

### Configuração

- `application.yml` — bloco `docgrid.queue`; `spring.profiles.group.local: worker` (em
  local, um processo corre API + worker; em AWS são serviços ECS separados: `aws` para a
  API, `aws,worker` para o worker); `logging.pattern.level`.
- `application-local.yml` / `-aws.yml` — `docgrid.queue.*` a apontar ao LocalStack / à AWS.
- `docker-compose.yml` — `SQS_ENDPOINT_STRATEGY: path` no LocalStack (URLs de fila
  previsíveis a partir do host).
- `backend/pom.xml` — `software.amazon.awssdk:sqs` (mesmo BOM, `2.54.13`).

### Infraestrutura (do outro agente — sem alteração)

`docker/localstack/init/ready.d/docgrid-resources.sh` cria bucket + fila + DLQ + política
de redrive (`maxReceiveCount=3`) + `VisibilityTimeout=120` + notificação `s3:ObjectCreated:*`.
Idempotente. O healthcheck do `docker compose` espera pelos três recursos.

### ADRs

- `docs/adr/0007-idempotencia-do-worker.md` — claim com chave do S3 como PK, contra
  verificação de estado e contra tabela de mensagens por `messageId`.
- `docs/adr/0008-escolha-de-sqs-e-desenho-do-worker.md` — SQS (contra trabalho agendado,
  processamento no pedido, motor de workflow), consumo à mão (contra `@SqsListener`),
  worker como perfil (contra módulo Maven), fronteira transitório/permanente.
- `docs/03-CONVENTIONS.md` — pacote `pipeline/` acrescentado.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Idempotência por **tabela de claims, chave do S3 = PK** | Só verificação de `document.status`; tabela de mensagens por `messageId` do SQS | A verificação de estado é uma corrida e não distingue "concluído" de "interrompido". O `messageId` muda no redrive da DLQ — dedup por ele reprocessava. A chave do S3 é a identidade do trabalho, 1:1 com o documento. ADR 0007. |
| Consumo **à mão sobre o `SqsClient`** | `@SqsListener` do Spring Cloud AWS | A etapa existe para mostrar a mecânica que o listener esconde (visibilidade, receive count, ack, limiar da DLQ). Dependência nova, fora da stack. ADR 0008. |
| Worker como **`@Profile("worker")`** no mesmo código-base | Módulo Maven separado | API e worker partilham domínio, migrações e armazenamento. Um módulo obrigava a um módulo comum e três poms, sem ganho a esta escala. ADR 0008. |
| `spring.profiles.group.local: worker` | Mudar `npm run up` para `-Dspring-boot.run.profiles=local,worker`; `@Profile("worker \| local")` no bean | O grupo mantém o comando do projeto intacto e não acopla o bean ao perfil `local`. |
| **Redrive reabre os documentos `FAILED`** antes de re-enfileirar | Redrive só devolve a mensagem à fila | Sem isso, a entrega seguinte via o documento já `FAILED` e a mensagem ressaltava para a DLQ — "reprocessável" não seria nada. |
| DLQ admin **sem autenticação** nesta etapa | Adiar o endpoint para a etapa 06 | O briefing pede o endpoint na 03. `/api/admin/**` é a costura que a 06 fecha com `@PreAuthorize`. |
| `list()` da DLQ com **visibilidade 0** (espreita) | Visibilidade curta (5s) | Uma listagem não deve esconder mensagens; e chamadas repetidas (Awaitility nos testes) partiam-se se ficassem invisíveis. |
| Correlação = **`documentId` no MDC + padrão de log** | Filtro servlet de `X-Correlation-Id` por pedido | O filtro por pedido e os logs estruturados são a etapa 10. Aqui basta o id que já atravessa os dois lados. |
| `DlqRedriveTest` com **contexto LocalStack próprio** (fila `maxReceiveCount=1`, visibilidade 0) | Reusar o contexto partilhado com a fila real (120s) | A fila real tornaria o teste de redrive lento (3×120s). Custo: um segundo container LocalStack na suite. |

ADRs escritos: `docs/adr/0007-idempotencia-do-worker.md`, `docs/adr/0008-escolha-de-sqs-e-desenho-do-worker.md`

## Como verificar

### Testes e formatação

O Testcontainers levanta Postgres e LocalStack (S3 + SQS); precisa do Docker Desktop.

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 148, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

De 128 para 148: **20 testes novos** — 9 em `S3EventNotificationParserTest` (unitário),
7 em `DocumentProcessorTest`, 2 em `DlqAdminControllerTest`, 1 em `PipelineFlowTest`,
1 em `DlqRedriveTest`. Continuam a aparecer as 4 linhas `ERROR ... duplicate key` da
etapa 01.

### Fluxo completo por `curl` — o critério de aceitação nº 1

`npm run up` (agora arranca API **e** worker, pelo grupo de perfis). Noutro terminal:

```bash
printf 'fatura de teste' > f.pdf
RESP=$(curl -s -XPOST localhost:8080/api/documents/upload-url \
  -H 'content-type: application/json' \
  -d "{\"filename\":\"f.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":$(wc -c < f.pdf)}")
DOC=$(echo "$RESP" | grep -o '"documentId":"[^"]*"' | cut -d'"' -f4)
URL=$(echo "$RESP" | grep -o '"uploadUrl":"[^"]*"' | cut -d'"' -f4)

curl -s -o /dev/null -w '%{http_code}\n' --upload-file f.pdf -H 'content-type: application/pdf' "$URL"
#  -> 200

# o worker consome a notificação sozinho; ao fim de 1-2 s:
docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select status from documents where id='$DOC'"
#  -> EXTRACTED

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select event_type||' '||coalesce(from_status,'-')||'->'||coalesce(to_status,'-') \
   from document_events where document_id='$DOC' order by id"
#  -> CREATED -->UPLOADED / STATUS_CHANGED UPLOADED->PROCESSING / STATUS_CHANGED PROCESSING->EXTRACTED

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select count(*) from extracted_fields where document_id='$DOC'"          # -> 7
docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select size_bytes is not null and file_hash is not null from documents where id='$DOC'"  # -> t
```

Nos logs, o id de correlação atravessa os dois lados:
`[doc=<uuid> msg=] ... DocumentUploadService` (thread da API) →
`[doc=<uuid> msg=<uuid>] ... DocumentProcessor` (thread `document-worker`).

### DLQ

```bash
curl -s localhost:8080/api/admin/dlq            # -> [] (ou as mensagens paradas)
curl -s -XPOST localhost:8080/api/admin/dlq/redrive   # -> {"movedToMainQueue":N}
```

**Executado nesta sessão** (app com perfis `local,worker` contra o LocalStack do
`docker compose`): o fluxo `curl` acima deu exatamente estas respostas; o documento de
teste foi apagado do Postgres/S3 locais no fim.

## O que ficou por fazer

Nada em falta bloqueia a etapa 04. Fora de âmbito por decisão:

- **Concorrência dentro do worker.** Processa uma mensagem de cada vez, de propósito. A
  etapa 04 decide o paralelismo quando houver trabalho pesado (Textract) para o
  justificar. ADR 0008.
- **Fronteira erro transitório / permanente na extração.** O `StubExtractor` nunca falha.
  A distinção "documento ilegível" (permanente) vs "serviço indisponível" (transitório)
  nasce na etapa 04, com o Textract.
- **Autenticação no endpoint da DLQ, RFC 7807 completo, OpenAPI** — etapa 06.
  `/api/admin/**` está aberto.
- **Filtro de correlação por pedido, logs estruturados (JSON), métricas** — etapa 10.
- **Terraform do SQS/DLQ/redrive reais, serviço ECS do worker, autoscaling** — etapa 11.
  O `docgrid-resources.sh` é a versão local do que o Terraform fará.
- **Botão "reprocessar" por documento** (a partir da fila de revisão) — etapa 06/08. O
  motor (`DocumentProcessor.reopenForReprocessing`) já existe; falta a UI e o endpoint.

## Armadilhas para a próxima sessão

1. **`QueueAttributeName` ≠ `MessageSystemAttributeName` no SDK v2.** Os atributos de uma
   *mensagem* (`ApproximateReceiveCount`) são `MessageSystemAttributeName`; os de uma
   *fila* (`VisibilityTimeout`, `RedrivePolicy`) são `QueueAttributeName`. Trocar não dá
   erro óbvio — `QueueAttributeName.APPROXIMATE_RECEIVE_COUNT` simplesmente não existe e o
   build parte. `message.attributes()` devolve `Map<MessageSystemAttributeName, String>`.
2. **`SQS_ENDPOINT_STRATEGY=path`** está no `docker-compose.yml` e nas configs de teste
   (`LocalStackPipelineConfiguration`, `DlqRedriveTest`). Sem isso, o LocalStack devolve
   URLs de fila com host `sqs.eu-west-1.localhost.localstack.cloud`, que às vezes não
   resolve a partir do host e com `endpointOverride` no cliente dá conflito. Se puxares
   estas alterações e `npm run infra` não recriar o container, força `npm run down && npm run infra`.
3. **Testes do pipeline correm o próprio script de init.** `LocalStackPipelineConfiguration`
   monta `docker/localstack/init/ready.d/docgrid-resources.sh` nas hooks do LocalStack e
   espera pela linha de log `DocGrid: bucket ...`. Se mudares o script, confirma que a
   última linha de `echo` continua a bater com o regex `.*DocGrid: bucket .*` da
   `waitingFor`. O caminho do script é relativo a `backend/` (diretório de trabalho do
   surefire).
4. **`DlqRedriveTest` levanta um segundo LocalStack.** Contexto próprio, com uma fila de
   `maxReceiveCount=1` e `VisibilityTimeout=0`, para o redrive acontecer em milissegundos
   em vez dos 120s da fila real. É a razão de a suite ter dois containers LocalStack a
   arrancar. Não os fundas — a fila rápida é incompatível com os testes que precisam do
   comportamento real.
5. **Região do LocalStack nos testes.** `LocalStackPipelineConfiguration` fixa
   `DEFAULT_REGION=eu-west-1` no container **e** nas propriedades, porque o script de init
   constrói ARNs de fila à mão (`arn:aws:sqs:${REGION}:...`) e o `container.getRegion()`
   do Testcontainers devolveria `us-east-1` por omissão — os ARNs deixariam de bater
   certo com a política de redrive.
6. **`s3:TestEvent`.** Ao configurar a notificação, o S3 (e o LocalStack) manda uma
   mensagem `{"Event":"s3:TestEvent"}` sem `Records`. O parser devolve lista vazia e o
   worker apaga-a **sem log**. Se vires a fila esvaziar sozinha sem nada nos logs, é isto.
7. **Um documento `FAILED` não se reprocessa sozinho.** A entrega seguinte da mensagem vê
   o estado `FAILED` e deixa a mensagem seguir para a DLQ (`Decision.LEAVE`). Reprocessar
   exige uma ação explícita — `reopenForReprocessing`, que o `redrive` chama. É de
   propósito: `FAILED` é onde um documento para até alguém decidir o contrário.
8. **`DocumentProcessor` usa `TransactionTemplate`, não `@Transactional`.** Três
   transações: reclamar (insere o claim + `PROCESSING`), trabalhar (fora de transação —
   S3 e extração não seguram uma ligação à BD), escrever (tudo o que o trabalho produziu).
   Não "simplifiques" para `@Transactional` num método só — o trabalho pesado ficaria com
   uma transação aberta.
9. **`ddl-auto: validate` + `V5__processing_claims.sql`.** A entidade `ProcessingClaim`
   não estende `BaseEntity` (chave natural, sem `updated_at`). Se lhe mexeres, a migração
   e a entidade têm de continuar a dizer o mesmo ou o contexto não sobe.

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/pipeline/DocumentWorker.java` — o consumidor. Se há um
  ficheiro para ler primeiro neste pacote, é este. `drainOnce()` é o passo testável.
- `backend/src/main/java/com/docgrid/pipeline/S3EventNotificationParser.java` — como se lê
  o evento do S3, e o que se recusa.
- `backend/src/main/java/com/docgrid/document/DocumentProcessor.java` — reclamar, ler,
  extrair, concluir; e `reopenForReprocessing`.
- `backend/src/main/java/com/docgrid/document/ProcessingClaim.java` — a tabela da
  idempotência. `completed_at` distingue "concluído" de "interrompido".
- `backend/src/main/java/com/docgrid/pipeline/DlqAdmin.java` — espreitar e reprocessar a DLQ.
- `backend/src/main/resources/application.yml` — bloco `docgrid.queue`, grupo de perfis
  `local: worker`, padrão de log com o id de correlação.
- `docker/localstack/init/ready.d/docgrid-resources.sh` — bucket, filas, redrive,
  notificação. A versão local do Terraform da etapa 11.
- `backend/src/test/java/com/docgrid/support/LocalStackPipelineConfiguration.java` —
  LocalStack S3+SQS de teste, a correr o script de init real.
- `backend/src/test/java/com/docgrid/pipeline/PipelineFlowTest.java` — o critério nº 1
  automatizado.
- `backend/src/test/java/com/docgrid/pipeline/DlqRedriveTest.java` — falha → DLQ →
  reprocessar → `EXTRACTED`.
- `docs/adr/0007-idempotencia-do-worker.md`, `docs/adr/0008-escolha-de-sqs-e-desenho-do-worker.md`

## Commits

```
a596ad0 build(infra): DLQ, redrive policy e notificação S3→SQS no arranque do LocalStack
9a4e166 feat(storage): leitura de objetos, com erro próprio para objeto inexistente
146f19d feat(extraction): interface de extração e stub com dados plausíveis
7cab683 feat(document): processador do worker com idempotência por claim
4f5b3c5 feat(pipeline): consumo assíncrono da fila SQS com worker e correlação de logs
5caf7c2 test(pipeline): idempotência do processador e fluxo ponta a ponta
7c29265 feat(pipeline): administração da dead-letter queue
dd0fc3b docs(adr): idempotência do worker e escolha de SQS
```

(Os quatro primeiros são do outro agente; `4f5b3c5` inclui a correção que os fazia
compilar. `7c29265` fez `reset --soft` de um commit anterior para juntar o reprocessamento
à administração da DLQ num só.)
