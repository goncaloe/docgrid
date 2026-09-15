# DocGrid — Roteiro

Treze etapas. Cada uma é uma sessão limpa do agente e termina com algo demonstrável.

| # | Etapa | Entrega verificável | Esforço |
|---|---|---|---|
| 00 | Fundações | `npm run up` levanta Postgres + LocalStack + API; `/health` responde | 1 sessão |
| 01 | Domínio e persistência | Migrações Flyway, entidades, máquina de estados testada | 1 sessão |
| 02 | Upload | URL pré-assinado, registo criado, ficheiro no S3 local | 1 sessão |
| 03 | Pipeline assíncrono | Evento S3 → SQS → worker; idempotência e DLQ | 1–2 sessões |
| 04 | Extração | Interface + stub local + Textract; campos com confiança | 1–2 sessões |
| 05 | Motor de validação | Regras de IVA, NIF, duplicados, confiança; encaminhamento de estado | 1 sessão |
| 06 | API e autenticação | REST completo, JWT, papéis, tratamento de erros RFC 7807 | 1–2 sessões |
| 07 | Frontend base | Login, layout, listagem de documentos, upload com progresso | 1–2 sessões |
| 08 | Ecrã de revisão | Documento original lado a lado com campos, destaque de confiança | 1–2 sessões |
| 09 | Dashboard e exportação | Gráficos por período/categoria, exportação CSV mensal | 1 sessão |
| 10 | Observabilidade | Logs estruturados com correlação, métricas, health checks | 1 sessão |
| 11 | IaC, CI/CD e AWS | Terraform escrito e não aplicado, GitHub Actions, imaxe multi-arquitectura no ghcr.io | 2–3 sessões |
| 12 | Vitrine | README, diagramas, dados de demonstração, vídeo, ADRs | 1 sessão |

Total realista: **6 a 10 semanas** em part-time. Se tiveres de cortar, corta a 09 e a 11
(fica em local, documenta a arquitetura AWS pretendida). Nunca cortes a 12 — um projeto
excelente mal apresentado avalia-se como um projeto medíocre.

## Encadeamento

```
00 ─▶ 01 ─▶ 02 ─▶ 03 ─▶ 04 ─▶ 05 ─▶ 06 ─┬─▶ 07 ─▶ 08 ─▶ 09
                                          └─▶ 10 ─▶ 11 ─▶ 12
```

As etapas 10 e 11 podem correr em paralelo com o frontend, se preferires alternar entre
backend e frontend para não saturar.

## Momentos de demonstração

Marcos em que o projeto já mostra alguma coisa a alguém:

- **Fim da 05** — o pipeline completo funciona por linha de comandos. É o coração técnico.
- **Fim da 08** — já é uma aplicação a sério, com o ecrã mais impressionante pronto.
- **Fim da 11** — CI/CD a sério (testes, análise estática, segredos, imaxe multi-arquitectura) e a infraestrutura AWS escrita e validada; sem conta AWS não há deploy nem URL público.
- **Fim da 12** — está pronto para pôr no CV.
