# Plano — Etapa 10: Observabilidade

**Data:** 2026-09-14 · **Planeado com:** Opus 5 · **Estado:** aprovado

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.
>
> E não é um documento morto: quem implementa escreve nele quando ele deixar de bater certo
> com o disco — ver "Desvios durante a execução", no fim.

## Contexto

O sistema está completo de ponta a ponta (upload → fila → worker → extração → validação →
revisão → dashboard → exportação) e não há forma de responder a "o que aconteceu ao
documento 4821?" sem abrir a base de dados. O que existe hoje de observabilidade:

- `backend/src/main/resources/application.yml` põe `documentId` e `messageId` no padrão de
  log (`logging.pattern.level`), alimentados por `MDC.put` em três sítios:
  `DocumentUploadService.authorizeUpload/fileUrl`, `DocumentProcessor.process` e
  `DocumentWorker.handle`. O comentário no ficheiro diz "a etapa 10 troca isto por logs
  estruturados a sério".
- `spring-boot-starter-actuator` está no `backend/pom.xml`, com `health,info` expostos e
  `show-details: when-authorized`. Não há indicador próprio nenhum: o único componente
  é o `db` que o Boot regista sozinho.
- Não há Micrometer registry nenhum, portanto não há `/actuator/prometheus`.

**Um facto do desenho que decide metade desta etapa:** quem produz a mensagem que o worker
consome **é o S3**, não a nossa API (ver o diagrama em `docs/02-ARCHITECTURE.md` e
`docker/localstack/init/ready.d/docgrid-resources.sh`, que configura a notificação
S3→SQS). Um evento `ObjectCreated` não carrega atributos nossos. Só as mensagens que nós
próprios enviamos — o redrive da DLQ, `SqsQueues.sendToMain` — os podem ter. Por isso a
correlação atravessa por dois caminhos, e não por um.

Verificado no disco durante o planeamento: o Spring Boot 3.5.16 traz **logging estruturado
nativo** (`org.springframework.boot.logging.logback.ElasticCommonSchemaStructuredLogFormatter`
e companhia, dentro do jar). Não é preciso `logstash-logback-encoder` nem qualquer
dependência de logging nova.

## O que se vai construir

1. **Id de correlação de ponta a ponta** — filtro HTTP que aceita ou gera um
   `correlationId`, o põe no MDC e o devolve no header da resposta; gravado em
   `documents.correlation_id` no upload e reposto pelo worker ao processar; propagado por
   *message attribute* nas mensagens que a aplicação envia (redrive da DLQ).
2. **Logs estruturados em JSON** (formato ECS), ligados no perfil `aws` e ligáveis em local
   por variável de ambiente; em local mantém-se a linha legível, agora com `cid=`.
3. **Cinco métricas Micrometer** e o endpoint `/actuator/prometheus`.
4. **Health checks próprios** de S3, SQS e extrator (o `db` já vem do Boot), com timeout
   curto e cache, e o alerta da DLQ como log de nível `WARN`.
5. **`GET /api/admin/pipeline/stats`** (só `ADMIN`) — o estado do pipeline agora, da mesma
   fonte que alimenta os gauges.

## Pré-requisitos verificados

- Pipeline completo: `backend/src/main/java/com/docgrid/pipeline/DocumentWorker.java`,
  `DlqAdmin.java`, `SqsQueues.java`, e `document/DocumentProcessor.java`.
- API com JWT e papéis: `auth/SecurityConfig.java` (`PUBLIC_PATHS` já inclui
  `/actuator/health` e `/actuator/info`), `auth/JwtTestSupport.java` para os testes.
- Actuator já é dependência (`backend/pom.xml`); falta só o registry do Prometheus.
- `DocumentStatus` é **público** (`document/DocumentStatus.java:31`) — os oito estados
  podem ser nomeados a partir de `pipeline` sem abrir nada.
- `StorageConfig.buildS3Client(StorageProperties)` e
  `SqsConfig.buildSqsClient(QueueProperties)` são estáticos — servem os testes unitários
  dos health checks.
- Migrações atuais vão até `V10__exports.sql`; a desta etapa é **V11**.
- Precedente para ler tabelas de outro pacote por SQL num read model:
  `docs/adr/0013-read-model-do-dashboard-e-exportacao.md` ("lê-se por SQL, escreve-se pelo
  pacote dono"). É a regra que autoriza `pipeline` a contar `documents`.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **Correlação por dois caminhos**: coluna `documents.correlation_id` (para os eventos do S3) **e** message attribute `X-Correlation-Id` (para o que a aplicação envia) | Só o attribute SQS, como o briefing sugeria | O produtor da mensagem do caminho normal é o S3: um attribute nosso nunca lá estaria. Só a coluna também não chegava — perdia-se o rasto do redrive, que é uma ação humana que vale a pena seguir |
| Formato do id: **UUID canónico** gerado no filtro; o header do cliente aceita-se saneado (máx. 64 chars, `[A-Za-z0-9_-]`) | Aceitar o header como vem; ou ULID/hex próprio | Saneamento evita injeção de texto arbitrário nos logs e cardinalidade infinita; UUID é o formato de id que o resto do projeto já usa |
| Manter o **`documentId`** no MDC ao lado do `correlationId` | Substituir um pelo outro | São coisas diferentes: o `documentId` liga tudo o que aconteceu a um documento ao longo de dias; o `correlationId` liga o que aconteceu num pedido. O critério de aceitação nº 1 é sobre o primeiro |
| **JSON (ECS) no perfil `aws`**; em local, a linha legível com `cid=`, ligável por `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` | JSON em todos os perfis | Ler JSON num terminal durante o desenvolvimento é pior em todos os aspetos; a variável dá a demonstração quando ela for precisa |
| Logging estruturado **nativo do Boot 3.5** | `logstash-logback-encoder` + `logback-spring.xml` próprio | Está no jar que já temos: zero dependências novas, zero XML. Confirmado no disco |
| `/actuator/health` e `/actuator/info` **públicos** (como hoje), **resto do actuator só `ADMIN`** | Deixar o resto acessível a qualquer autenticado; ou porta de gestão separada | Métricas dizem volume de negócio e profundidade de filas — não é informação para qualquer empregado. A porta de gestão separada (`management.server.port`) é a solução de produção, mas pertence à etapa 11, com o ALB à frente |
| Alerta da DLQ = **log `WARN` quando a contagem muda** + métrica `docgrid.queue.depth{queue="dlq"}` | Pôr o health check a `DOWN` quando a DLQ tem mensagens | Na etapa 11 o health é a probe do ECS: um `DOWN` por causa da DLQ mataria um container perfeitamente saudável. Uma mensagem presa é um problema de negócio, não de saúde do processo |
| **Cinco métricas** e não mais (tabela abaixo) | Instrumentar tudo o que se mexe | O briefing pede "poucas e boas"; cada métrica a mais é cardinalidade e ruído em CloudWatch, que se paga por série |
| Taxa de automação como **contador com etiqueta** (`docgrid.documents.extracted{review}`) | Gauge com o rácio já calculado | Um gauge de rácio mente depois de um reinício e não permite janelas temporais; dois contadores dão o rácio em qualquer janela com `rate()` |
| Gauges de base de dados e de fila registados **em todos os processos** | Registá-los só na API (`@Profile("!worker")`) | Em local a API e o worker são o mesmo processo e o perfil `local` inclui `worker` — restringir por perfil deixava o desenvolvimento sem métricas. Em AWS há duas séries iguais: agrega-se com `max by (status)`, nunca `sum`. Fica escrito no ADR |
| Health do extrator **sem chamar o Textract** | Uma chamada de teste ao Textract | As análises pagam-se por documento e o Textract não tem operação de ping. O indicador reporta "configurado", com a região, e diz que não contactou o serviço — honesto é melhor do que verde |
| Cada health check no **pacote da dependência que verifica** (`storage`, `pipeline`, `extraction`) | Um pacote novo `com.docgrid.observability` com tudo | `docs/03-CONVENTIONS.md`: organiza-se por funcionalidade, não por camada técnica. Quem sabe o que é o S3 estar saudável é o pacote que fala com o S3 |
| `PipelineStatsService` lê `documents` **por SQL** a partir de `pipeline` | Abrir `DocumentRepository` ao pacote `pipeline` | É exatamente o precedente do ADR-0013; a alternativa obrigava a tornar público o que hoje é package-private em `document` |

ADRs a escrever: `docs/adr/0015-id-de-correlacao.md`,
`docs/adr/0016-metricas-e-health-checks.md`

### As cinco métricas

| Métrica | Tipo | Etiquetas | Onde se escreve |
|---|---|---|---|
| `docgrid.documents.count` | Gauge (`MultiGauge`) | `status` (os 8 de `DocumentStatus`) | `pipeline/PipelineMetrics.java`, cada 30 s |
| `docgrid.extraction.duration` | Timer | `outcome` = `success` \| `unreadable` \| `error` | `extraction/ExtractionMetrics.java` |
| `docgrid.documents.extracted` | Counter | `review` = `required` \| `none` | `document/DocumentMetrics.java` |
| `docgrid.queue.depth` | Gauge | `queue` = `main` \| `dlq` | `pipeline/PipelineMetrics.java`, cada 30 s |
| `docgrid.queue.in_flight` | Gauge | `queue` = `main` \| `dlq` | `pipeline/PipelineMetrics.java`, cada 30 s |

## Riscos e pontos de paragem

- **O MDC no teste de propagação passa por acidente.** Worker e upload correm na mesma JVM
  e no mesmo fio nos testes: se o teste não fizer `MDC.clear()` antes de `drainOnce()`, ele
  passa sem provar nada. O `MDC.clear()` é parte do teste, não um detalhe.
- **Contexto de teste novo = LocalStack novo = minutos.** Os testes que precisam de S3+SQS
  têm de repetir **exatamente** as anotações de `PipelineFlowTest`
  (`@SpringBootTest` + `@ActiveProfiles("test")` + `@Import({PostgresContainerConfiguration.class,
  LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})`) para reutilizar
  o contexto em cache. Qualquer anotação diferente (um `@MockitoBean`, um
  `properties = {...}`) cria um contexto novo. → se precisares de um contexto com
  propriedades diferentes, **para e pergunta** antes de o criar.
- **Health checks lentos derrubam o `/actuator/health`.** O SDK da AWS tenta três vezes com
  backoff: sem `apiCallTimeout` por pedido, o endpoint fica pendurado ~30 s com o LocalStack
  desligado. Todos os indicadores usam `overrideConfiguration(o -> o.apiCallTimeout(...))`
  com 3 s, e o endpoint tem cache de 10 s.
- **`logging.structured.format.console` com valor vazio.** Não ponhas a propriedade em
  `application.yml` com valor vazio para "ser configurável": põe-na só em
  `application-aws.yml`. Em local liga-se pela variável de ambiente
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`. → se o arranque local partir por causa desta
  propriedade, **para e pergunta** em vez de inventar um `logback-spring.xml`.
- **O worker sem `correlationId`.** Um documento anterior a esta etapa tem `correlation_id`
  nulo. É normal, não é erro: o MDC fica sem a chave e o padrão imprime `cid=`. Não
  inventes um id novo no worker — isso criaria um rasto falso.
- **Alterar a interface `DocumentExtractor`** obriga as duas implementações e os duplos de
  teste a responder. Se aparecer um terceiro implementador que não conheces (procura
  `implements DocumentExtractor` em `backend/src/test`), **para e pergunta**.
- **`@EnableScheduling` num processo que corre a API e o worker** faz o refresh das métricas
  correr uma vez só — é o desejado. Não ponhas `@Scheduled` em beans com estado partilhado
  além dos contadores atómicos deste plano.
- Se `npm test` falhar em massa logo no arranque, confirma `docker info` antes de procurar
  regressões: com o Docker em baixo, todas as classes de integração falham a carregar
  contexto (armadilha 1 do handoff da etapa 09).

## Passos de implementação

Por ordem. Cada passo é uma unidade de commit.

1. **Id de correlação em todos os pedidos HTTP** — criar
   `backend/src/main/java/com/docgrid/shared/Correlation.java` e
   `backend/src/main/java/com/docgrid/shared/CorrelationIdFilter.java`; alterar
   `backend/src/main/resources/application.yml`
   - `Correlation`: classe final utilitária, pública. Constantes `MDC_KEY = "correlationId"`,
     `HEADER = "X-Correlation-Id"`, `SQS_ATTRIBUTE = "X-Correlation-Id"`; `MAX_LENGTH = 64`;
     padrão `^[A-Za-z0-9_-]{1,64}$`. Métodos: `sanitizeOrGenerate(String candidate)` (devolve
     o candidato se casar com o padrão, senão `UUID.randomUUID().toString()`), `current()`
     (o valor do MDC ou `null`), `set(String)`, `clear()`.
   - `CorrelationIdFilter`: `@Component`, `@Order(Ordered.HIGHEST_PRECEDENCE)`,
     `extends OncePerRequestFilter`. Lê o header, sanea ou gera, `Correlation.set(...)`,
     escreve o mesmo valor no header da resposta, e `Correlation.clear()` no `finally`.
     A ordem tem de ser anterior à cadeia do Spring Security (que corre a −100), para que
     um 401 já saia com id de correlação.
   - `application.yml`: `logging.pattern.level` passa a
     `"%5p [cid=%X{correlationId:-} doc=%X{documentId:-} msg=%X{messageId:-}]"`.
   - Testes: `backend/src/test/java/com/docgrid/shared/CorrelationIdFilterTest.java`
     (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` +
     `@Import(PostgresContainerConfiguration.class)`, como `DlqAdminAuthorizationTest`) —
     a resposta traz o header quando o pedido não o traz; o header do cliente é ecoado tal e
     qual; um header com lixo é substituído por um UUID; o MDC fica limpo depois do pedido.
   - Commit: `feat(shared): id de correlação em todos os pedidos`

2. **O documento guarda o id de correlação que o criou** — criar
   `backend/src/main/resources/db/migration/V11__document_correlation_id.sql`; alterar
   `document/Document.java`, `document/DocumentUploadService.java`,
   `document/DocumentProcessor.java`
   - Migração: `alter table documents add column correlation_id varchar(64);` com comentário
     a dizer que é o elo entre os logs da API e os do worker, e que é nulo para documentos
     anteriores a esta etapa. Sem índice: procura-se nos logs, não em SQL.
   - `Document`: campo `@Column(name = "correlation_id", length = 64, updatable = false)
     private String correlationId;`, novo parâmetro final `String correlationId` em
     `forUpload(...)`, e getter package-private `getCorrelationId()`.
   - `DocumentUploadService.authorizeUpload`: passa `Correlation.current()` a `forUpload`.
     O `MDC.put("documentId", ...)` que lá está mantém-se.
   - `DocumentProcessor`: o record interno `Claim` ganha o campo `correlationId`, lido do
     documento nos dois ramos de `claim()`/`decisionForClaimed()`. Em `process(...)`, junto
     ao `MDC.put("documentId", ...)` que já existe: **só** se `Correlation.current() == null`
     e o documento tiver correlação, `Correlation.set(claim.correlationId())`; no `finally`,
     limpar apenas o que este método pôs. O attribute da mensagem (passo 3) tem precedência.
   - Testes: `document/DocumentUploadServiceTest.java` (existe) ganha um teste em que, com
     `Correlation.set("upload-abc")`, o documento gravado fica com `correlation_id` igual —
     lido como a classe já lê o resto do estado.
   - Commit: `feat(document): documento guarda o id de correlação do upload`

3. **Correlação nos atributos da mensagem** — alterar `pipeline/SqsQueues.java`,
   `pipeline/DocumentWorker.java`
   - `SqsQueues.sendToMain(String body)`: quando `Correlation.current() != null`, envia
     `messageAttributes(Map.of(Correlation.SQS_ATTRIBUTE, MessageAttributeValue.builder()
     .dataType("String").stringValue(id).build()))`.
   - `SqsQueues.receiveFromMain()`: acrescentar `.messageAttributeNames("All")` — sem isto o
     SQS não devolve atributos de utilizador (os `messageSystemAttributeNames` que já lá
     estão são outra coisa).
   - `DocumentWorker.handle(Message)`: além do `MDC.put("messageId", ...)` que já existe,
     lê `message.messageAttributes().get(Correlation.SQS_ATTRIBUTE)` e, se existir, faz
     `Correlation.set(saneado)`; no `finally`, `Correlation.clear()`.
   - Commit: `feat(pipeline): correlação propagada nos atributos da mensagem`

4. **O teste que interessa: a correlação sobrevive à fila** — criar
   `backend/src/test/java/com/docgrid/pipeline/CorrelationPropagationTest.java`
   - Anotações **idênticas** às de `PipelineFlowTest` (ver Riscos), para reutilizar o
     contexto e o LocalStack já em cache.
   - Teste A (caminho do S3, elo pela base de dados): `Correlation.set("upload-abc")`,
     `uploads.authorizeUpload(...)`, `PUT` do ficheiro para o URL pré-assinado como em
     `PipelineFlowTest`, **`MDC.clear()`**, anexar um `ch.qos.logback.core.read.ListAppender`
     ao logger `com.docgrid`, correr `new DocumentWorker(...).drainOnce()` dentro de um
     `await()` até receber ≥ 1 mensagem, e afirmar que existe um evento de
     `DocumentProcessor` com `getMDCPropertyMap().get("correlationId")` igual a
     `"upload-abc"`. Remover o appender no fim.
   - Teste B (caminho do envio nosso, elo pelo attribute): `Correlation.set("redrive-xyz")`,
     `queues.sendToMain(corpoDeUmEventoValido)`, `MDC.clear()`, receber com
     `queues.receiveFromMain()` e afirmar que a mensagem traz o attribute com
     `"redrive-xyz"`. Apagar a mensagem no fim para não a deixar na fila.
   - Commit: `test(pipeline): o id de correlação sobrevive à passagem pela fila`

5. **Logs estruturados em JSON** — alterar `backend/src/main/resources/application-aws.yml`
   e o comentário em `application.yml`
   - `application-aws.yml`: `logging.structured.format.console: ecs`. Nada mais: o Boot
     substitui o encoder e passa a escrever uma linha JSON por evento, com o MDC em campos
     de topo (`correlationId`, `documentId`, `messageId`).
   - `application.yml`: o comentário do `logging.pattern.level` passa a explicar que em
     local é texto legível e que `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` liga o JSON.
   - Sem teste automatizado: é configuração de um formatador do Boot, e um teste do formato
     de saída testaria o Boot, não o DocGrid. A verificação é manual, nos critérios.
   - Commit: `feat(logging): logs estruturados em JSON no perfil aws`

6. **Registry do Prometheus, agendamento e actuator fechado a não-administradores** —
   alterar `backend/pom.xml`, `auth/SecurityConfig.java`, `application.yml`; criar
   `shared/SchedulingConfig.java`
   - `pom.xml`: dependência `io.micrometer:micrometer-registry-prometheus` com
     `<scope>runtime</scope>` (versão gerida pelo BOM do Boot; **não** escrever versão).
   - `SchedulingConfig`: `@Configuration(proxyBeanMethods = false) @EnableScheduling`, com
     javadoc a dizer que serve o refresh periódico das métricas.
   - `application.yml`: `management.endpoints.web.exposure.include: health,info,prometheus,metrics`
     e `management.endpoint.health.cache.time-to-live: 10s`.
   - `SecurityConfig`: entre `PUBLIC_PATHS` e `anyRequest()`, acrescentar
     `.requestMatchers("/actuator/**").hasRole("ADMIN")`. `/actuator/health` e
     `/actuator/info` continuam públicos porque `PUBLIC_PATHS` é avaliado primeiro.
   - Testes: `backend/src/test/java/com/docgrid/shared/ActuatorSecurityTest.java` (mesmas
     anotações de `DlqAdminAuthorizationTest`) — `/actuator/prometheus` sem token dá 401,
     com token `EMPLOYEE` dá 403, com `ADMIN` dá 200 e o corpo contém `jvm_`;
     `/actuator/health` sem token dá 200.
   - Commit: `feat(api): métricas Prometheus atrás do papel de administrador`

7. **Estatísticas do pipeline** — criar `pipeline/PipelineStatsService.java`,
   `pipeline/PipelineStatsController.java`, `pipeline/dto/PipelineStats.java`; alterar
   `pipeline/SqsQueues.java`
   - `SqsQueues`: método público `depths(Duration apiCallTimeout)` que devolve o record
     público `QueueDepths(QueueDepth main, QueueDepth dlq)` com
     `QueueDepth(int available, int inFlight)`, lidos de `getQueueAttributes` com
     `APPROXIMATE_NUMBER_OF_MESSAGES` e `APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE` e com
     `overrideConfiguration(o -> o.apiCallTimeout(apiCallTimeout))`. Deixa passar a
     `SdkException` — quem chama decide o que fazer com a fila em baixo.
   - `PipelineStatsService`: `@Service` público, `JdbcClient` + `SqsQueues`.
     `documentsByStatus()` → `select status, count(*) from documents group by status`,
     devolvido como `Map<String, Long>` com os oito valores de `DocumentStatus` presentes
     (zero incluído, para o gauge não desaparecer). `current()` → `PipelineStats`.
     Timeout do SQS: 5 s.
   - `dto/PipelineStats.java`: record público
     `PipelineStats(Map<String, Long> documentsByStatus, Queue main, Queue dlq)` com
     `Queue(String name, int available, int inFlight)`.
   - `PipelineStatsController`: `@RestController @RequestMapping("/api/admin/pipeline")
     @PreAuthorize("hasRole('ADMIN')")`, package-private, `@GetMapping("/stats")`. Mesmo
     desenho de `DlqAdminController`.
   - Testes: `backend/src/test/java/com/docgrid/pipeline/PipelineStatsControllerTest.java`
     (anotações de `DlqAdminAuthorizationTest`, com `@MockitoBean PipelineStatsService`) —
     401 sem token, 403 com `FINANCE`, 200 com `ADMIN` e o corpo com os campos esperados.
   - Commit: `feat(pipeline): endpoint de estatísticas do pipeline`

8. **Métricas do pipeline e alerta da DLQ** — criar `pipeline/PipelineMetrics.java`
   - `@Component` package-private, recebe `MeterRegistry` e `PipelineStatsService`.
   - No construtor: `MultiGauge` `docgrid.documents.count`; quatro `AtomicInteger` para
     `docgrid.queue.depth{queue}` e `docgrid.queue.in_flight{queue}`, registados com
     `Gauge.builder(...)`.
   - `@Scheduled(fixedRate = 30_000)` `refresh()`: lê `PipelineStatsService.current()`,
     atualiza o `MultiGauge` (`MultiGauge.Row.of(Tags.of("status", ...), valor)`) e os
     atómicos.
   - Alerta: guarda a última contagem da DLQ num campo; loga `WARN` **só quando o valor
     muda** para maior que zero ou entre valores positivos ("A DLQ tem {} mensagens
     paradas..."), e `INFO` quando volta a zero. Um `WARN` a cada 30 s com a mesma
     informação é ruído, não alerta.
   - Falha do SQS (LocalStack desligado): apanhar `SdkException` e logar a `debug`, deixando
     os gauges no último valor conhecido. Quem reporta a fila em baixo é o health check —
     um `WARN` a cada 30 s durante o desenvolvimento seria insuportável.
   - Testes: `backend/src/test/java/com/docgrid/pipeline/PipelineMetricsTest.java` —
     unitário, com `SimpleMeterRegistry` e um `PipelineStatsService` em duplo (Mockito):
     depois de `refresh()`, `registry.get("docgrid.documents.count").tag("status",
     "NEEDS_REVIEW").gauge().value()` tem o valor esperado e `docgrid.queue.depth` idem;
     e um segundo `refresh()` com a DLQ na mesma contagem não repete o aviso (verificado
     com um `ListAppender`).
   - Commit: `feat(pipeline): métricas de estado, filas e alerta da DLQ`

9. **Duração da extração e taxa de automação** — criar `extraction/ExtractionMetrics.java`
   e `document/DocumentMetrics.java`; alterar `document/DocumentProcessor.java`
   - `ExtractionMetrics`: `@Component` público, `MeterRegistry`. Método
     `ExtractionResult time(Supplier<ExtractionResult> extraction)` que mede com
     `System.nanoTime()` e regista em `Timer.builder("docgrid.extraction.duration")
     .tag("outcome", ...)`: `success` no caminho normal, `unreadable` quando sai
     `UnreadableDocumentException`, `error` para qualquer outra `RuntimeException`
     (relançando sempre — a métrica não muda o comportamento).
   - `DocumentMetrics`: `@Component` package-private em `document`, com
     `void recordExtraction(boolean requiresReview)` que incrementa
     `docgrid.documents.extracted` com `review` = `required` ou `none`.
   - `DocumentProcessor`: injeta os dois; a chamada passa a
     `ExtractionResult result = extractionMetrics.time(() -> extractor.extract(content, claim.contentType()));`
     e, em `complete(...)`, depois de calcular o `ValidationSummary`,
     `documentMetrics.recordExtraction(summary.requiresReview())`.
   - Testes: `backend/src/test/java/com/docgrid/extraction/ExtractionMetricsTest.java` —
     unitário com `SimpleMeterRegistry`: um sucesso conta em `outcome=success`; uma
     `UnreadableDocumentException` conta em `outcome=unreadable` e **é relançada**.
   - Commit: `feat(extraction): duração da extração e taxa de automação como métricas`

10. **Health checks próprios** — criar `storage/S3HealthIndicator.java`,
    `pipeline/SqsHealthIndicator.java`, `extraction/ExtractorHealthIndicator.java` e
    `extraction/ExtractorStatus.java`; alterar `extraction/DocumentExtractor.java`,
    `extraction/StubExtractor.java`, `extraction/TextractExtractor.java`
    - O nome do componente no `/actuator/health` vem do nome do bean sem o sufixo
      `HealthIndicator`: as classes acima dão `s3`, `sqs` e `extractor`. O `db` continua a
      ser o do Boot.
    - `S3HealthIndicator`: `@Component` package-private, `implements HealthIndicator`.
      `s3.headBucket(b -> b.bucket(props.bucket()).overrideConfiguration(o ->
      o.apiCallTimeout(Duration.ofSeconds(3))))` → `Health.up().withDetail("bucket", ...)`;
      `SdkException` → `Health.down(e).withDetail("bucket", ...)`.
    - `SqsHealthIndicator`: `@Component` package-private; usa
      `queues.depths(Duration.ofSeconds(3))` → `up()` com as duas filas e as contagens como
      detalhe; `SdkException` → `down(e)` com o nome das filas.
    - `ExtractorStatus`: record público `(String engine, boolean ready, String detail)`.
    - `DocumentExtractor`: novo método `ExtractorStatus status();` (sem implementação por
      omissão — são duas implementações e ambas têm o que dizer).
    - `StubExtractor.status()`: `engine = "stub"`, `ready` = a fixture configurada carrega,
      `detail` = o caminho da fixture. `TextractExtractor.status()`: `engine = "textract"`,
      `ready = true`, `detail` = a região e a nota de que o serviço **não** foi contactado.
    - `ExtractorHealthIndicator`: traduz `ExtractorStatus` em `Health` (`up`/`down` +
      detalhes). É o actuator a depender da extração, nunca o contrário.
    - Testes:
      `backend/src/test/java/com/docgrid/storage/S3HealthIndicatorTest.java` e
      `backend/src/test/java/com/docgrid/pipeline/SqsHealthIndicatorTest.java` — unitários,
      sem Spring: cliente construído com `StorageConfig.buildS3Client` /
      `SqsConfig.buildSqsClient` sobre propriedades apontadas a `http://localhost:1`;
      o resultado é `DOWN` e o detalhe identifica a causa. (Cobre o critério de aceitação
      "parar o LocalStack põe o health check a `DOWN`" sem parar container nenhum.)
      `backend/src/test/java/com/docgrid/extraction/ExtractorHealthIndicatorTest.java` —
      o stub reporta `UP` com o caminho da fixture.
      `backend/src/test/java/com/docgrid/shared/HealthEndpointTest.java` — com LocalStack a
      correr (anotações de `PipelineFlowTest`), `/actuator/health` autenticado como `ADMIN`
      traz os componentes `db`, `s3`, `sqs` e `extractor`, todos `UP`.
    - Commit: `feat(health): S3, SQS e extrator reportam-se separadamente`

11. **Documentação** — criar `docs/adr/0015-id-de-correlacao.md` e
    `docs/adr/0016-metricas-e-health-checks.md`; alterar `docs/02-ARCHITECTURE.md`,
    `docs/03-CONVENTIONS.md`, `README.md`
    - ADR 0015: o produtor da mensagem é o S3, os dois caminhos da correlação, o formato e o
      saneamento, o `documentId` como id de longa duração, e o tracing distribuído como
      evolução posta de lado.
    - ADR 0016: as cinco métricas e porque não mais; contador em vez de gauge de rácio; os
      gauges duplicados nos dois processos e a regra `max by (status)`; o health da DLQ que
      **não** existe de propósito; o extrator que não contacta o Textract.
    - `docs/02-ARCHITECTURE.md`: secção "Observabilidade" curta, a seguir a "Segurança".
    - `docs/03-CONVENTIONS.md`: em "Regras de código", o que é o MDC do projeto
      (`correlationId`, `documentId`, `messageId`) e que nenhum log de pipeline sai sem
      `documentId`.
    - `README.md`: como ver os logs em JSON localmente, onde estão as métricas e o que o
      `/api/admin/pipeline/stats` devolve; estado da etapa 10.
    - Commit: `docs(adr): id de correlação, métricas e health checks`

## Critérios de aceitação

```bash
# 1. Suite completa (precisa do Docker Desktop aberto: Testcontainers)
docker info > /dev/null && npm test

# 2. Formatação
npm run lint
```
Resultado esperado: tudo verde, incluindo `CorrelationPropagationTest`.

Com a aplicação a correr (`npm run up`), autenticado como `ADMIN` em `$TOKEN`:

```bash
# 3. Health por dependência
curl -s localhost:8080/actuator/health -H "Authorization: Bearer $TOKEN" | jq '.components | keys'
# esperado: ["db","diskSpace","extractor","ping","s3","sqs"] (ou superconjunto)

# 4. Métricas
curl -s localhost:8080/actuator/prometheus -H "Authorization: Bearer $TOKEN" | grep docgrid_
# esperado: docgrid_documents_count, docgrid_queue_depth, docgrid_queue_in_flight,
#           docgrid_extraction_duration_seconds*, docgrid_documents_extracted_total

# 5. Métricas fechadas a quem não é administrador
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/prometheus
# esperado: 401

# 6. Estatísticas do pipeline
curl -s localhost:8080/api/admin/pipeline/stats -H "Authorization: Bearer $TOKEN" | jq

# 7. LocalStack em baixo ⇒ DOWN com a causa
docker compose stop localstack
curl -s localhost:8080/actuator/health -H "Authorization: Bearer $TOKEN" | jq '.status, .components.s3, .components.sqs'
# esperado: "DOWN" e a causa identificada em cada um; o componente db continua UP
docker compose start localstack

# 8. A história completa de um documento, API e worker incluídos
#    (submeter um documento pela UI e depois procurar o id no log da aplicação:)
grep "<documentId>" <log da aplicação>
# esperado: as linhas do upload (API) e as do processamento (worker), todas com o mesmo
# doc=<documentId>, e as do upload com o mesmo cid=<correlationId>

# 9. Logs em JSON
LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs npm run up
# esperado: uma linha JSON por evento, com correlationId e documentId em campos de topo
```

## Fora de âmbito

- **Tracing distribuído (OpenTelemetry / Micrometer Tracing)** → mencionado como evolução no
  ADR 0015. Não vale o esforço num sistema com dois processos, e o briefing exclui-o.
- **CloudWatch: log groups, alarme da DLQ, dashboards; probes de liveness e readiness;
  porta de gestão separada (`management.server.port`)** → etapa 11. O comentário em
  `application.yml` sobre as probes já diz isso.
- **Prometheus e Grafana no `docker-compose.yml`** → evolução documentada no ADR 0016. O
  critério de aceitação é o endpoint existir, não haver uma stack de monitorização local a
  gastar memória.
- **Ecrã de administração no frontend** que consuma `/api/admin/pipeline/stats` → não existe
  área de administração no frontend (ver `frontend/src/App.tsx`), e criá-la não está em
  etapa nenhuma do roteiro. Se for para mostrar, entra na etapa 12.
- **Reutilizar `LocalStackContainerConfiguration` nos testes que arrancam o LocalStack à
  mão** → dívida herdada do handoff da etapa 09, não é desta etapa.

## Desvios durante a execução

Preenchido por **quem implementa, à medida que acontece** — não no fim, não no handoff.
Quando o plano deixar de bater certo com o disco, corrige-se aqui e o problema fica visível
para quem retomar.

| Passo | O que o plano dizia | O que ficou | Detalhe ou decisão |
|---|---|---|---|
| 1 | `Correlation` com `MAX_LENGTH = 64` e padrão de saneamento | A classe acrescentou o método público `isValid(String)` (o padrão e o `MAX_LENGTH` ficaram privados); o `CorrelationIdFilterTest` afirma o padrão UUID com um regex próprio | Detalhe de execução |
| 3 | Worker faz `Correlation.set(saneado)` do attribute | O worker só propaga quando `Correlation.isValid(...)` — valor inválido é ignorado, nunca substituído por um UUID novo, para não criar rasto falso (o mesmo princípio do risco "o worker sem correlationId") | Detalhe de execução |
| 4 | Teste B: "receber com `queues.receiveFromMain()` e afirmar que a mensagem traz o attribute" | O LocalStack deixa o `s3:TestEvent` do arranque na fila; o teste filtra as mensagens que têm o attribute (exatamente 1) e apaga todas as recebidas | Detalhe de execução |
| 6 | `ActuatorSecurityTest` "com as mesmas anotações de `DlqAdminAuthorizationTest`" | O `@SpringBootTest` do Boot 3.x desliga as exportações de métricas por defeito (`DisableObservabilityContextCustomizer` põe `management.defaults.metrics.export.enabled=false` — sem isto o `/actuator/prometheus` nem é criado, 404); o teste precisa de `@AutoConfigureObservability` | Detalhe de execução |
| | | | |
