# Etapa 10 — Observabilidade

## Objetivo
Conseguir responder a "o que aconteceu ao documento 4821?" olhando para os logs, e ver o
estado do sistema sem entrar na base de dados.

## Porque conta
Separa quem já operou software em produção de quem só o escreveu. É barata de implementar
e muito visível para quem avalia.

## Contexto a carregar
`docs/02-ARCHITECTURE.md`, handoff da etapa mais recente

## Pré-requisitos
Pipeline completo (etapa 05) e API (etapa 06).

## Âmbito
- Logging estruturado em JSON, com id de correlação que atravessa API → SQS → worker
- O `documentId` presente em todos os logs relevantes do pipeline
- Métricas Micrometer: documentos por estado, duração da extração, taxa de automação,
  profundidade da fila, mensagens na DLQ
- Health checks próprios: base de dados, S3, SQS, extrator
- Endpoint de estatísticas do pipeline para o dashboard de administração
- Alerta simples: mensagens na DLQ acima de zero
- Testes: o id de correlação sobrevive à passagem pela fila (é o teste que interessa)

## Fora
Tracing distribuído completo com OpenTelemetry — menciona como evolução. Não vale o esforço
num sistema com dois processos.

## Decisões desta etapa
- Formato do id de correlação e como o propagar através de atributos de mensagem SQS
- Que métricas merecem mesmo existir (poucas e boas)

## Critérios de aceitação
- [ ] `grep` por um documentId mostra a história completa do documento, API e worker incluídos
- [ ] `/actuator/health` reporta cada dependência separadamente
- [ ] As métricas aparecem em `/actuator/prometheus`
- [ ] Parar o LocalStack põe o health check a `DOWN` com a causa identificada

## Esforço estimado
1 sessão
