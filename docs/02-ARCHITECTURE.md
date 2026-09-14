# DocGrid — Arquitetura

## Vista geral

```
   Browser (React)
        │
        │ 1. pede autorização de upload
        ▼
   ┌─────────────────┐        2. upload direto      ┌──────────┐
   │  API Spring     │◀────────────────────────────▶│    S3    │
   │  (REST + JWT)   │                              └────┬─────┘
   └────────┬────────┘                                   │ 3. evento
            │                                            ▼
            │                                      ┌──────────┐
            │                                      │   SQS    │──▶ DLQ
            │                                      └────┬─────┘
            │                                           │ 4. consome
            │                                           ▼
            │                                    ┌──────────────┐
            │                                    │   Worker     │
            │                                    │  (Spring)    │
            │                                    └──────┬───────┘
            │                                           │ 5. extrai
            │                                           ▼
            │                                    ┌──────────────┐
            │                                    │  Textract    │
            │                                    └──────┬───────┘
            ▼                                           │ 6. valida e guarda
   ┌─────────────────────────────────────────────────────────────┐
   │                    PostgreSQL (RDS)                          │
   └─────────────────────────────────────────────────────────────┘
```

API e worker vivem no mesmo repositório e partilham o modelo de domínio, mas correm como
processos separados (perfis Spring `api` e `worker`). Escalam independentemente e um pico
de processamento não degrada a API.

## Decisões estruturantes

**Upload direto para o S3 com URL pré-assinado.** O ficheiro nunca passa pelo servidor Java.
Poupa largura de banda e memória, e é como se faz em produção. O backend cria o registo em
`UPLOADED` no momento em que emite a autorização, para que nada suba sem rasto.

**Processamento assíncrono via SQS.** Extração demora 5 a 30 segundos. Fazê-la no pedido HTTP
prende o pedido e perde tudo se algo falhar. Com fila há retries, visibilidade e uma DLQ para
o que falhar repetidamente. É a decisão mais valiosa do projeto do ponto de vista de portefólio.

**Idempotência por chave do S3.** A mesma mensagem pode ser entregue mais que uma vez —
o SQS garante *at-least-once*, não *exactly-once*. Antes de processar, o worker verifica se
aquele objeto já foi processado. Sem isto, duplicam-se faturas.

**Extração atrás de uma interface.** `DocumentExtractor` tem duas implementações: `TextractExtractor`
(AWS) e `StubExtractor` (local, devolve dados fixos a partir de ficheiros JSON). Testes e
desenvolvimento local nunca chamam a AWS.

**Validação como motor de regras separado.** As regras vivem em `ValidationRule`, cada uma
independente e testável isoladamente. Adicionar uma regra nova não toca no código existente.

**PostgreSQL para tudo.** Os dados são relacionais e as consultas do dashboard são agregações.
Não há aqui nada que justifique NoSQL — e escolher a base certa pelas razões certas é melhor
sinal técnico do que usar DynamoDB para impressionar.

## Modelo de dados (esboço)

- `organizations` — dados da empresa, limite de aprovação
- `users` — email, hash da palavra-passe, papel, organização
- `documents` — chave S3, estado, quem submeteu, timestamps, hash do ficheiro
- `extracted_fields` — documento, nome do campo, valor, confiança, origem (`AI` ou `HUMAN`)
- `validation_results` — documento, regra, resultado, mensagem
- `document_events` — auditoria de todas as transições
- `suppliers` — NIF, nome, categoria habitual, contagem de ocorrências
- `exports` — período, ficheiro gerado, documentos incluídos

## Ambientes

| | Local | AWS |
|---|---|---|
| Armazenamento | LocalStack S3 | S3 |
| Fila | LocalStack SQS | SQS + DLQ |
| Extração | `StubExtractor` | Textract |
| Base de dados | Postgres em Docker | RDS Postgres |
| Execução | Docker Compose | ECS Fargate |

O objetivo é `npm run up` levantar tudo localmente sem uma única credencial AWS real.

## Segurança

JWT com refresh token. Papéis `EMPLOYEE`, `FINANCE`, `MANAGER`, `ADMIN`. Autorização ao nível
do método (`@PreAuthorize`) e filtragem por organização em todas as consultas.
Ficheiros no S3 são privados: o acesso é sempre por URL pré-assinado de curta duração.

## Observabilidade

Cada pedido HTTP leva um id de correlação (`X-Correlation-Id`, UUID saneado) que atravessa
a linha por dois caminhos — pela coluna `documents.correlation_id` no caminho normal (o
produtor da mensagem é o S3, que não carrega atributos) e por atributo de mensagem no que
a aplicação envia (o redrive da DLQ). Ver `docs/adr/0015-id-de-correlacao.md`.

Os logs são estruturados em JSON (ECS) no perfil `aws`; em local a linha é legível com
`cid=`, `doc=` e `msg=`. As métricas vivem em `/actuator/prometheus` (só `ADMIN`) e o
estado do pipeline em `GET /api/admin/pipeline/stats`. O health reporta cada dependência
separadamente (`db`, `s3`, `sqs`, `extractor`). Ver `docs/adr/0016-metricas-e-health-checks.md`.
