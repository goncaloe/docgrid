# Etapa 01 — Domínio e persistência

## Objetivo
O modelo de dados e a máquina de estados dos documentos, com migrações Flyway e testes.
Ainda sem API nem upload.

## Porque conta
É a espinha do projeto. Um modelo de dados bem pensado — com auditoria, com estados
explícitos, com confiança guardada por campo — mostra mais maturidade que qualquer
funcionalidade vistosa construída sobre um modelo confuso.

## Contexto a carregar
`docs/01-PRODUCT.md` (integral), `docs/02-ARCHITECTURE.md` (modelo de dados),
`docs/03-CONVENTIONS.md`, handoff da etapa 00

## Pré-requisitos
Etapa 00: aplicação arranca, Flyway configurado, Testcontainers a funcionar.

## Âmbito
- Migrações Flyway para: `organizations`, `users`, `documents`, `extracted_fields`,
  `validation_results`, `document_events`, `suppliers`
- Entidades JPA correspondentes, com índices pensados (procura por estado, por NIF+número)
- `DocumentStatus` como enum e uma máquina de estados que **rejeita transições inválidas**
  com exceção de domínio própria
- Registo automático em `document_events` a cada transição: quem, quando, de onde para onde, porquê
- Repositórios Spring Data com as consultas que as etapas seguintes vão precisar
- Testes: máquina de estados exaustiva (todas as transições válidas e uma amostra das inválidas),
  repositórios com Testcontainers

## Fora
Upload, extração, validação, API. Esta etapa não expõe nada ao exterior.

## Decisões desta etapa
- Estados como coluna de texto vs enum nativo do Postgres — recomenda e justifica
- Campos extraídos em tabela própria (uma linha por campo) vs colunas na tabela `documents`.
  A tabela própria permite guardar confiança e origem por campo; discute o custo.
- Soft delete vs nunca apagar

## Critérios de aceitação
- [ ] `npm run up` aplica todas as migrações num Postgres vazio
- [ ] Tentar `APPROVED → PROCESSING` lança exceção de domínio, com teste a prová-lo
- [ ] Cada transição escreve um evento de auditoria, com teste a prová-lo
- [ ] Um campo extraído guarda valor, confiança e origem (`AI` ou `HUMAN`)
- [ ] Existe um ADR sobre o desenho de `extracted_fields`

## Esforço estimado
1 sessão · o plano é longo, lê-o com atenção antes de avançar
