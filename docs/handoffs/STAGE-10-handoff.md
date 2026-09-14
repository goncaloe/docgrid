# Handoff — Etapa 10: Observabilidade

**Data:** 2026-09-14 · **Sessão:** #14 · **Estado:** completa
**Caminho:** duas sessões (planear / implementar)

## O que ficou feito

### Correlação de ponta a ponta
- `shared/Correlation.java` + `shared/CorrelationIdFilter.java` — id de correlação UUID
  saneado (máx 64, `[A-Za-z0-9_-]`), no MDC (`correlationId`) e no header `X-Correlation-Id`;
  o filtro corre antes do Spring Security (um 401 já sai identificado).
- `db/migration/V11__document_correlation_id.sql` — `documents.correlation_id` (nulo para
  documentos anteriores); o upload grava-o e o worker restaura-o ao processar.
- `pipeline/SqsQueues.java` + `pipeline/DocumentWorker.java` — o id viaja como atributo de
  mensagem no que a aplicação envia (redrive da DLQ); o worker devolve-o ao MDC.
- `pipeline/CorrelationPropagationTest.java` — o teste que interessa: a correlação
  sobrevive à fila pelos dois caminhos (BD e atributo).

### Métricas e estado
- `pipeline/PipelineStatsService.java` + `dto/PipelineStats.java` + `PipelineStatsController.java`
  — `GET /api/admin/pipeline/stats` (só `ADMIN`), da mesma fonte dos gauges.
- `pipeline/PipelineMetrics.java` — gauges `docgrid.documents.count` (MultiGauge, 30 s),
  `docgrid.queue.depth/in_flight`, e o alerta da DLQ (`WARN` na mudança de contagem).
- `extraction/ExtractionMetrics.java` — `docgrid.extraction.duration` por `outcome`
  (success/unreadable/error); `document/DocumentMetrics.java` — `docgrid.documents.extracted`
  por `review` (contadores, não gauge de rácio).
- `pom.xml` — `micrometer-registry-prometheus` (runtime); `shared/SchedulingConfig.java`;
  actuator expõe `health,info,prometheus,metrics`, o resto só `ADMIN`
  (`SecurityConfig`).

### Health e logs
- `storage/S3HealthIndicator.java`, `pipeline/SqsHealthIndicator.java`,
  `extraction/ExtractorHealthIndicator.java` + `ExtractorStatus.java` — componentes `s3`,
  `sqs`, `extractor` no `/actuator/health`, timeout 3 s; o extrator não contacta o Textract.
- `application-aws.yml` — logs estruturados JSON (ECS); em local a linha fica legível com
  `cid=/doc=/msg=`; `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` liga o JSON.
- ADRs `0015-id-de-correlacao.md`, `0016-metricas-e-health-checks.md`; secção
  "Observabilidade" em `docs/02-ARCHITECTURE.md`; regra do MDC em `docs/03-CONVENTIONS.md`;
  README atualizado.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Correlação por **dois caminhos**: coluna `correlation_id` (caminho do S3) **e** atributo de mensagem (o que a aplicação envia) | Só o atributo; ou só a coluna | O produtor do caminho normal é o S3, que não carrega atributos nossos; só a coluna perdia o rasto do redrive |
| Logs **ECS só no perfil `aws`**; local legível, ligável por variável | JSON em todos os perfis | Ler JSON no terminal em dev é pior; o Boot 3.5 traz o formatador nativo, zero dependências |
| **5 métricas** (tabela no ADR 0016) | Instrumentar tudo o que se mexe | Cada série em CloudWatch paga-se; "poucas e boas" |
| Taxa de automação como **contador com etiqueta** | Gauge de rácio | O rácio mente depois de reinício e não dá janelas; dois contadores dão `rate()` |
| **DLQ não derruba o health**; alerta = `WARN` na mudança | Health a `DOWN` com a DLQ cheia | Na etapa 11 o health é probe do ECS; um DOWN mataria um container saudável |
| Health do extrator **sem chamar o Textract** | Ping de teste | Análises pagam-se; o Textract não tem ping; "configurado" é mais honesto |
| Cada health check **no pacote da dependência** | `com.docgrid.observability` novo | Convenção: por funcionalidade, não por camada |

ADRs escritos: `docs/adr/0015-*.md`, `docs/adr/0016-*.md`

## Desvios ao plano

Plano seguido: `docs/plans/STAGE-10-plano.md`

**8 detalhes de execução · 0 decisões que obrigaram a parar · 1 grupo de problemas só apanhados na suite completa**

Todos os desvios do plano foram detalhes de execução (registados na secção "Desvios durante a
execução" do plano); nenhum exigiu parar e perguntar. O único grupo que só apareceu ao correr
a **suite completa** (não nos testes individuais) foi o de integração entre testes:

- **`CorrelationPropagationTest` duplicava o `PipelineFlowTest`.** Ambos submetem o mesmo
  conteúdo binário e a regra de duplicados por hash marcava o segundo documento como
  `NEEDS_REVIEW`. Resolvido com um conteúdo próprio no teste da correlação.
- **5 testes que usavam `/actuator/health` como "endpoint público 200"** passaram a receber
  503: com os health checks novos, o health reflete as dependências, e esses contextos não
  têm LocalStack (S3/SQS em baixo). `CorrelationIdFilterTest` passou a usar `/actuator/info`;
  `ActuatorSecurityTest.healthIsPublic` aceita UP ou DOWN; `ApplicationContextTest` aceita
  UP ou DOWN; `HealthEndpointTest` ganhou o caso "health público sem token".

## Como verificar

```bash
# backend — 324 testes (precisa do Docker Desktop aberto: Testcontainers)
npm test
# formatação e análise estática
npm run lint
```

Com a aplicação a correr (`npm run up`), autenticado como `ADMIN` em `$T` (o `register`
cria o primeiro utilizador como ADMIN):

```bash
curl -s localhost:8080/actuator/health -H "Authorization: Bearer $T" | jq '.components | keys'
# esperado: ["db","diskSpace","extractor","ping","s3","sqs","ssl"] (ou superconjunto), todos UP

curl -s localhost:8080/actuator/prometheus -H "Authorization: Bearer $T" | grep '^docgrid'
# docgrid_documents_count{status=...}, docgrid_queue_depth{queue=...}, ...

curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/prometheus   # 401

curl -s localhost:8080/api/admin/pipeline/stats -H "Authorization: Bearer $T" | jq
# os 8 estados + as duas filas

LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs npm run up   # uma linha JSON por evento ECS
```

Resultado esperado: 324/324 verdes, lint limpo; manualmente os componentes do health, as
métricas `docgrid_*`, o 401 do prometheus, as stats e o JSON ECS confirmados.

## O que ficou por fazer

- **Etapa 11** — CloudWatch (log groups, alarme da DLQ, dashboards), probes de liveness e
  readiness, porta de gestão separada (`management.server.port`). O comentário em
  `application.yml` sobre as probes já o anuncia. Não bloqueia nada desta etapa.
- Nada desta etapa fica por implementar.

## Armadilhas para a próxima sessão

1. **`@AutoConfigureObservability` em qualquer teste que toque métricas/actuator.** O
   `@SpringBootTest` do Boot 3.x desliga as exportações de métricas por omissão
   (`DisableObservabilityContextCustomizer` põe `management.defaults.metrics.export.enabled=false`);
   sem a anotação, o `/actuator/prometheus` nem é criado (404).
2. **MockMvc manual não corre o Spring Security.** `MockMvcBuilders.webAppContextSetup(ctx)`
   sem `@AutoConfigureMockMvc` não tem o filtro de segurança, e o `show-details:
   when-authorized` do health esconde os detalhes (só `{"status":"UP"}`). Junta-se o
   `springSecurityFilterChain` como filter explícito — e evita o contexto novo que o
   `@AutoConfigureMockMvc` criaria (LocalStack novo, minutos).
3. **A BD de teste é partilhada** entre classes com o mesmo contexto (cache). A regra de
   duplicados por **hash binário** aciona quando dois testes submetem o mesmo conteúdo
   (o `PipelineFlowTest` e o `CorrelationPropagationTest`). Conteúdo de teste próprio, ou o
   segundo documento é `NEEDS_REVIEW`.
4. **`/actuator/health` deixou de ser garantidamente 200** em contextos sem LocalStack
   (S3/SQS em baixo → 503). Para um "endpoint público estável" em testes usa-se
   `/actuator/info`.
5. **`ListAppender` do logback** expõe o campo público `list` (não `getList()`), nesta
   versão.
6. **`MultiGauge` vazio não emite** no `/actuator/prometheus` até ter pelo menos uma linha
   (por isso `docgrid_documents_count` não aparece sem um refresh com dados).
7. **Health checks contactam a AWS real** em contextos de teste sem LocalStack (endpoint
   vazio → default). Com timeout de 3 s dão DOWN rápido e o `PipelineMetrics` apanha a
   `SdkException` a `debug` — correto, mas há tráfego para a AWS real a cada pedido ao
   health / refresh de métricas nesses testes.

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/shared/Correlation.java` — o id de correlação e o MDC.
- `backend/src/main/java/com/docgrid/pipeline/CorrelationPropagationTest.java` — o teste que
  prova que a correlação atravessa a fila pelos dois caminhos.
- `backend/src/main/java/com/docgrid/pipeline/PipelineStatsService.java` + `dto/PipelineStats.java`
  — a fonte única dos gauges e do endpoint de stats (lê `documents` por SQL, ADR 0013).
- `backend/src/main/java/com/docgrid/pipeline/PipelineMetrics.java` — gauges e alerta da DLQ.
- `backend/src/main/java/com/docgrid/extraction/ExtractionMetrics.java` — duração por desfecho.
- `backend/src/main/java/com/docgrid/storage/S3HealthIndicator.java`,
  `pipeline/SqsHealthIndicator.java`, `extraction/ExtractorHealthIndicator.java` — os health
  checks por pacote.

## Commits

```
4cc0fc6 feat(shared): id de correlação em todos os pedidos
bd2b473 feat(document): documento guarda o id de correlação do upload
d2eb7bc feat(pipeline): correlação propagada nos atributos da mensagem
d7e5006 test(pipeline): o id de correlação sobrevive à passagem pela fila
3e0656e feat(logging): logs estruturados em JSON no perfil aws
1ca2a8a feat(api): métricas Prometheus atrás do papel de administrador
8758040 feat(pipeline): endpoint de estatísticas do pipeline
34f6389 feat(pipeline): métricas de estado, filas e alerta da DLQ
2ae6b03 feat(extraction): duração da extração e taxa de automação como métricas
cc4de76 feat(health): S3, SQS e extrator reportam-se separadamente
7b70079 docs(adr): id de correlação, métricas e health checks
763228b fix(test): testes robustos com os health checks e a BD partilhada
```
