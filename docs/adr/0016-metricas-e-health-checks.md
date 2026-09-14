# 0016 — Métricas e health checks

**Estado:** aceite · **Data:** 2026-09-14

## Contexto

A etapa 10 quer "ver o estado do sistema sem entrar na base de dados". Duas perguntas
de desenho: que métricas merecem existir (o briefing pede "poucas e boas"), e o que cada
health check deve — e não deve — reportar.

## Decisão 1 — Cinco métricas, e não mais

| Métrica | Tipo | Etiquetas | Onde se escreve |
|---|---|---|---|
| `docgrid.documents.count` | Gauge (`MultiGauge`) | `status` (os 8 de `DocumentStatus`) | `pipeline/PipelineMetrics`, cada 30 s |
| `docgrid.extraction.duration` | Timer | `outcome` = `success` \| `unreadable` \| `error` | `extraction/ExtractionMetrics` |
| `docgrid.documents.extracted` | Counter | `review` = `required` \| `none` | `document/DocumentMetrics` |
| `docgrid.queue.depth` | Gauge | `queue` = `main` \| `dlq` | `pipeline/PipelineMetrics`, cada 30 s |
| `docgrid.queue.in_flight` | Gauge | `queue` = `main` \| `dlq` | `pipeline/PipelineMetrics`, cada 30 s |

Cada métrica a mais é cardinalidade e ruído em CloudWatch, que se paga por série — daí o
teto de cinco. A taxa de automação é um **contador com etiqueta**
(`docgrid.documents.extracted{review=required|none}`) e não um gauge de rácio: um gauge
de rácio mente depois de um reinício e não permite janelas temporais; dois contadores dão
o rácio em qualquer janela com `rate()`.

Os gauges de base de dados e de fila registam-se **em todos os processos**. Em local a API
e o worker são o mesmo processo; em AWS há duas séries iguais — agrega-se com
`max by (status)`, nunca `sum` (senão os contadores duplicavam).

## Decisão 2 — O que o health reporta

- **Cada dependência no seu pacote**: `s3` em `storage`, `sqs` em `pipeline`, `extractor`
  em `extraction`; o `db` é o do Boot. Organiza-se por funcionalidade, não por camada.
- **Timeouts curtos (3 s) e cache de 10 s no endpoint**: sem `apiCallTimeout`, o SDK
  tenta três vezes com backoff e o `/actuator/health` fica pendurado ~30 s com o serviço
  em baixo.
- **A DLQ não derruba o health, de propósito.** Na etapa 11 o health é a probe do ECS:
  um `DOWN` por causa da DLQ mataria um container perfeitamente saudável. Uma mensagem
  presa é um problema de negócio — o alerta é o `WARN` nas métricas quando a contagem
  muda, e a profundidade vem no detalhe do health.
- **O extrator não contacta o Textract**: as análises pagam-se por documento e o Textract
  não tem operação de ping. O indicador reporta "configurado" com a região, e diz que
  não contactou o serviço — honesto é melhor do que verde.

## Alternativas consideradas

- **Instrumentar tudo o que se mexe**: mais ruído e mais séries pagas do que valor.
- **Health da DLQ a `DOWN`**: contradiz o papel do health como sonda de processo.
- **Chamada de teste ao Textract**: custo por documento, sem operação de ping — e a
  decisão de produção foi não pagar análises de teste.
- **Um pacote novo `com.docgrid.observability`**: contraria a convenção de organizar por
  funcionalidade; quem sabe o que é o S3 estar saudável é o pacote que fala com o S3.

## Consequências

- `GET /api/admin/pipeline/stats` (só `ADMIN`) expõe o mesmo estado dos gauges — a fonte
  única é `PipelineStatsService`, que lê `documents` por SQL (o precedente do ADR 0013).
- Prometheus e Grafana em `docker-compose` ficam de fora desta etapa: o critério é o
  endpoint existir, não haver uma stack de monitorização local a gastar memória.
