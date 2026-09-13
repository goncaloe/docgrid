# Handoff — Etapa 09: Dashboard e exportação

**Data:** 2026-09-14 · **Sessão:** #12 · **Estado:** parcial — falta frontend e ADRs

## O que ficou feito

### Categoria gravada na aprovação — `backend/`

- `V8__document_category.sql` — coluna `category varchar(50)` em `documents`.
- `Document.java` — campo `category`, getter, `void categoriseAs(String)`.
- `DocumentApprovalService.java` — na aprovação, grava categoria na projeção, cria/atualiza
  `extracted_fields` CATEGORY com origem HUMAN (sem confiança), e emite
  `FIELD_CORRECTED` se o valor mudou (inclusive de nulo).
- `FieldCorrectionService.java` — depois de `projectInvoiceFields`, projeta também
  a categoria a partir do `CATEGORY` field, para os dois caminhos de escrita não divergirem.
- Testes: `DocumentApprovalServiceTest` (2 novos: aprovar com/sem categoria),
  `FieldCorrectionServiceTest` (1: corrigir CATEGORY projeta na coluna).

### Escrita direta no S3 — `backend/`

- `StorageService.java` — `void put(String key, String contentType, byte[] content)`.
- `S3StorageService.java` — `s3.putObject(PutObjectRequest, RequestBody.fromBytes(content))`.
- Teste: `S3StorageServiceTest` (1: put seguido de download devolve os mesmos bytes).

### Agregações do dashboard — `backend/src/main/java/com/docgrid/dashboard/`

- `V9__document_aggregation_index.sql` — índice em `(organization_id, status, issue_date)`.
- `package-info.java` — read model, só lê.
- `DashboardQueries.java` — 5 consultas com `JdbcClient`: `periodTotals`, `operations`,
  `monthlyTotals`, `categoryTotals`, `topSuppliers`. Todas filtram por `organization_id`.
- `DashboardService.java` — `@Transactional(readOnly = true)`, monta `DashboardResponse`.
- `DashboardController.java` — `@RestController @RequestMapping("/api/dashboard")`,
  `@PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")`, parâmetros `from`/`to`
  (`YearMonth`, default últimos 12 meses).
- `dto/DashboardResponse.java` — record principal + 5 nested records (`PeriodTotals`,
  `Operations`, `MonthlyTotal`, `CategoryTotal`, `SupplierTotal`).
- Testes: `DashboardQueriesTest` (6), `DashboardControllerTest` (3), `DashboardPerformanceTest`
  (1 — 5 ms com 5000 docs, log registado).

### Exportação mensal — `backend/src/main/java/com/docgrid/export/`

- `V10__exports.sql` — tabela `exports` (`id`, `organization_id`, `period_year`,
  `period_month`, `storage_key`, `document_count`, `net_total`, `vat_total`, `total`,
  `created_by`, timestamps) com índice único `uq_exports_org_period`; `export_id` em
  `documents` com índice parcial.
- `Document.java` — `void markExported(UUID exportId)` e campo `export_id`.
- `DocumentExportService.java` — `@Transactional`, `markExported(orgId, exportId, docIds, actor)`,
  transita cada documento para `EXPORTED` e grava o evento.
- `Export.java` — entidade JPA, package-private, com `static withId(...)` fábrica (reflexão
  no campo privado de `BaseEntity` para fixar o PK).
- `ExportRepository.java` — `findByOrganizationIdAndPeriodYearAndPeriodMonth`,
  `findByOrganizationIdAndPeriodYearOrderByPeriodMonthDesc`, `findByIdAndOrganizationId`.
- `ExportQueries.java` — `JdbcClient`: seleciona `APPROVED` sem `export_id` para exportar,
  conta `APPROVED` sem `issue_date`.
- `ExportCsvWriter.java` — `@Component`, `byte[] write(List<ExportRow>)`: UTF-8 BOM,
  `;`, `\r\n`, `dd-MM-yyyy`, vírgula decimal, RFC 4180 quoting.
  Cabeçalho: `Data;Nº da fatura;Fornecedor;NIF;Base tributável;Taxa IVA;IVA;Total;Categoria;Moeda;Ficheiro`.
- `ExportService.java` — `@Transactional`: recusa períodos futuros (409), verifica existente
  (idempotente), gera CSV, `storage.put()`, grava `Export` + chama `DocumentExportService`.
  `DataIntegrityViolationException` catch no caso de condição de corrida.
- `ExportController.java` — `@PostMapping` (201/200 `ResponseEntity`), `@GetMapping` (lista
  por ano), `@GetMapping("/{id}/file-url")`.
- `dto/ExportResponse.java`, `dto/CreateExportRequest.java`, `dto/ExportFileUrlResponse.java`.
- `PeriodNotClosableException.java` — `extends DomainException`, devolve 409.
- `ExportNotFoundException.java` — devolve 404.
- Testes: `ExportCsvWriterTest` (8 — unitários, sem Spring), `ExportServiceTest` (6 — com
  Postgres + LocalStack via `LocalStackContainerConfiguration`).

Fora da etapa: `LocalStackContainerConfiguration` foi criado para os testes de exportação
e `DocumentProcessorTest` deveria poder reusá-lo (ver Armadilhas).

## Decisões tomadas

As decisões estão no plano (`docs/plans/STAGE-09-plano.md`). Ver ADRs abaixo — estão por
escrever (faltam ADR-0013 e ADR-0014), mas as decisões foram implementadas conforme o plano.

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| `integer` em vez de `smallint` na V10 | `smallint` como o plano previa | A entidade JPA `Export` mapeia `int` → `integer` (int4); o Hibernate schema-validation rejeitava `smallint` (int2). Corrigido durante a implementação |
| `ExportCsvWriter` como `@Component` (instância) | Classe puramente estática | O `ExportService` injeta-o como bean; `write()` passou de estático para método de instância. Os testes (`ExportCsvWriterTest`) foram ajustados para instanciar a classe |

ADRs a escrever: `docs/adr/0013-read-model-do-dashboard-e-exportacao.md` e
`docs/adr/0014-fecho-de-periodo-e-formato-do-csv.md` — planeados para o passo 7.

## Desvios ao plano

Plano seguido: `docs/plans/STAGE-09-plano.md`

- **`ExportCsvWriter` tornou-se `@Component`**, não uma utility puramente estática, porque
  o `ExportService` precisa de o injetar. O `write()` passou a método de instância.
  Isto permitiu injeção direta sem refletir sobre `static`.
- **`ExportController.create()` usa `ResponseEntity`** para devolver 201 na primeira chamada
  e 200 na segunda (período já fechado), em vez de um `@ResponseStatus(HttpStatus.CREATED)
  fixo que colocaria 201 sempre.
- **`period_year`/`period_month` em `integer`** na V10 e não `smallint` — ver decisões.
- **`Export.withId()` usa reflexão** para escrever o campo `id` privado de `BaseEntity`.
  Alternativa seria expor um `setId()` em `BaseEntity`, mas isso abriria a porta a erros
  noutras entidades.
- **V10 adiciona `updated_at` à tabela `exports`** — o plano omitiu-o. A entidade estende
  `BaseEntity` que tem o campo, e sem ele a JPA rejeita a entidade.

## Como verificar

```bash
# backend — 300 testes
npm test

# frontend (quando implementado)
cd frontend
npx tsc -b --noEmit
npx eslint .
npx vitest run
npm run build
```

Resultado esperado: 300/300 testes verdes, BUILD SUCCESS.

## O que ficou por fazer

- **Frontend dashboard** — `@mantine/charts`, componentes (`KpiRow`, `MonthlyChart`,
  `CategoryDonut`, `TopSuppliers`), `DashboardPage`, rota `/dashboard`.
- **Frontend exportações** — `ExportsPage`, `ConfirmCloseModal`, rota `/exports`, descarga
  por URL pré-assinado.
- **ADR-0013** — read model do dashboard (agregar em SQL, pacote `dashboard` como só leitura).
- **ADR-0014** — fecho de período e formato CSV (BOM, `;`, vírgula decimal, lançamentos
  extemporâneos, não reabrir).
- **`docs/03-CONVENTIONS.md`** — acrescentar `dashboard/` à árvore de pacotes.
- **`docs/01-PRODUCT.md`** — secção sobre exportação e fecho de período.
- **`README.md`** — SAF-T como evolução possível.
- **GIF do ecrã para `docs/assets/`** — continua por fazer desde a etapa 08.
- **`LocalStackContainerConfiguration`** não está a ser usado por `DocumentProcessorTest`
  e outros testes que também arrancam LocalStack — podem beneficiar de o reutilizar.

## Armadilhas para a próxima sessão

1. **`smallint` vs `int` na JPA.** Se criares uma coluna `smallint` no Postgres, a JPA
   mapeia `int`/`Integer` → `integer` (int4) e o *schema-validation* do Hibernate rejeita
   a diferença de tipo. Usa `integer` na migração ou mapeia o campo como `Short`.
2. **`@TestPropertySource` cria contextos separados.** O `ExportServiceTest` usa
   `@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")` que cria uma
   entrada diferente no cache de contextos Spring. Se falhar, só os testes com essa
   combinação exata de anotações ficam afetados.
3. **`DataIntegrityViolationException` na exportação concorrente.** O `ExportService.create()`
   primeiro verifica se existe, depois insere. Em condição de corrida, a segunda inserção
   viola o índice único e o catch devolve a exportação que ganhou. A ordem é: `storage.put()`
   primeiro, depois a transação da base de dados — assim um objeto órfão no S3 não tem
   consequência funcional.
4. **`LocalStackContainerConfiguration`** é um `@TestConfiguration` que cria o bucket
   automaticamente via `awslocal s3 mb`. Pode ser reutilizado por qualquer teste que precise
   de S3 real — evita duplicar o container e a criação do bucket.
5. **Categoria com `null` no `documents.category`** é tratada como "Sem categoria" no
   frontend. A API devolve `null` no campo `category` — o rótulo é responsabilidade do
   frontend, nunca da API (lição 5 do handoff 08).

## Ficheiros centrais desta etapa

- `backend/.../dashboard/DashboardQueries.java` — as 5 consultas SQL com `JdbcClient`.
- `backend/.../dashboard/DashboardPerformanceTest.java` — prova de que < 1000 ms com 5000 docs.
- `backend/.../export/ExportService.java` — orquestra o fecho de período.
- `backend/.../export/ExportCsvWriter.java` — gera o CSV com BOM e formatação pt-PT.
- `backend/.../export/ExportQueries.java` — seleciona documentos para exportar.
- `backend/.../document/DocumentApprovalService.java` — alterado para gravar categoria.

## Commits

```
e9e3723 feat(api): endpoints de exportação mensal
b0f8cd7 feat(export): fecho de período idempotente
0b491e2 feat(export): escrita do CSV no formato esperado pelo contabilista
1933b35 feat(export): entidade e consultas da exportação mensal
c857e45 feat(document): documento guarda a exportação em que foi fechado
e0af3b5 feat(db): tabela de exportações e ligação aos documentos
bf67d04 feat(api): agregações do dashboard com taxa de automação
e98e460 feat(document): categoria do documento gravada na aprovação
```