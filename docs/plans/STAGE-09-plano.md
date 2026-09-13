# Plano — Etapa 09: Dashboard e exportação

**Data:** 2026-09-13 · **Planeado com:** Opus 5 · **Estado:** aprovado

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.

## Contexto

A etapa 08 fechou o ecrã de revisão: há documentos aprovados no sistema e ninguém lhes
pega a partir daí. Faltam as duas pontas que fecham o ciclo do produto — o número que diz
se o sistema está a valer a pena (**taxa de automação**: quantos documentos passaram sem
mão humana) e o ficheiro mensal que o contabilista externo recebe, que é a única forma
como o DocGrid toca o processo real da empresa.

No caminho apareceu um pré-requisito em falta: **a categoria não é gravada em lado nenhum
do documento**. O revisor escolhe-a ao aprovar, `DocumentApprovalService.approve` recebe-a,
mas ela só alimenta `suppliers.usual_category` — não fica no documento nem em
`extracted_fields`. Sem isso não há repartição por categoria no dashboard nem coluna de
categoria no CSV, e o pré-preenchimento da categoria no ecrã de revisão
(`ReviewPage.tsx:44`) lê um campo que nunca existe. Decidido: passa a ser gravada.

## O que se vai construir

1. **Categoria persistida na aprovação** — coluna `documents.category` (projeção, para
   agregar) e linha `extracted_fields` CATEGORY com origem HUMAN (a verdade, ADR-0003).
2. **Read model do dashboard** — pacote novo `com.docgrid.dashboard`, só leitura, SQL puro:
   evolução mensal, repartição por categoria, principais fornecedores, e os indicadores de
   topo (documentos processados, taxa de automação, tempo médio de revisão, IVA do período).
3. **Exportação mensal em CSV** — pacote `com.docgrid.export` (hoje só tem `package-info`):
   gera o CSV, grava-o no S3, regista a exportação em `exports`, e passa os documentos
   incluídos a `EXPORTED` pela porta pública do pacote `document`.
4. **Ecrã de dashboard** com a taxa de automação em destaque, e **ecrã de fecho de período**
   com a lista de exportações e descarga do ficheiro.
5. **Dois ADRs** — o read model, e o fecho de período com a regra dos lançamentos
   extemporâneos.

## Pré-requisitos verificados

- Documentos aprovados existem: `DocumentApprovalService` (etapa 08), transição
  `APPROVED → EXPORTED` já prevista em `DocumentStatus.java:68` e em `docs/01-PRODUCT.md`.
- `exports` está previsto em `docs/02-ARCHITECTURE.md` ("Modelo de dados") e o pacote
  `backend/src/main/java/com/docgrid/export/package-info.java` já existe, vazio.
- `documents` tem as colunas de projeção para agregar (`net_amount`, `vat_amount`,
  `vat_rate`, `total_amount`, `issue_date`) — `V3__documents.sql`, que diz explicitamente
  que `vat_rate` é percentagem "como o contabilista a espera no CSV da etapa 09".
- Descarga por URL pré-assinado já existe ponta a ponta: `StorageService.createDownloadUrl`,
  `DocumentUploadService.fileUrl`, `frontend/src/features/review/useDocumentFile.ts`.
- Migrações atuais vão até `V7__refresh_tokens.sql`; as desta etapa são V8, V9 e V10.
- **Em falta, e por isso está no âmbito:** `StorageService` não sabe escrever um objeto
  (só emitir URLs e ler) — o CSV é gerado pelo servidor, precisa de um `put`.
- **Em falta, e por isso está no âmbito:** a categoria do documento (ver Contexto).

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Agregar em **SQL** (`JdbcClient`, sem entidades) | Carregar documentos e agregar em Java | Somar 5000 linhas em memória para mostrar 12 pontos num gráfico é trabalho a mais e memória a mais; `group by` com índice é o que a base de dados faz melhor. Em Java seria também impossível sem abrir as entidades de `document` a outro pacote |
| Pacote novo `com.docgrid.dashboard`, **read model**: só lê, nunca escreve, SQL sobre `documents`/`document_events`/`suppliers` | Pôr as agregações dentro de `document` | `document` já tem 60+ ficheiros e trata do ciclo de vida; um read model não partilha nada com ele além das tabelas. Regra que fica escrita na ADR-0013: **ler por SQL é permitido a um read model; escrever é só pelo pacote dono** |
| `export` lê as suas linhas por SQL e **escreve só por `DocumentExportService`** (público, no pacote `document`) | `export` a mexer em `documents.status` diretamente | A transição tem de passar por `DocumentStatus.canTransitionTo` e deixar evento em `document_events`. Uma segunda porta para mudar estado apagava a garantia que a etapa 01 construiu |
| **Um** endpoint `GET /api/dashboard` que devolve os quatro blocos | Quatro endpoints (`/monthly`, `/categories`, `/suppliers`, `/summary`) | O ecrã carrega como uma unidade e o critério de aceitação é o ecrã em menos de um segundo. Com quatro pedidos, mudar o período mostra blocos de períodos diferentes durante uns instantes — os números do topo deixariam de bater certo com os gráficos |
| Período de um documento = **mês da `issue_date`** para dinheiro; `created_at` para os indicadores operacionais | Usar `created_at` para tudo | O contabilista trabalha por data da fatura, não pela data em que alguém a fotografou. Já a taxa de automação e o tempo de revisão são sobre o trabalho do mês, logo `created_at` |
| A exportação do mês M inclui tudo o que está `APPROVED`, sem exportação, com `issue_date` **anterior ao fim de M** | Só `issue_date` dentro de M | Um documento aprovado tarde, de um mês já fechado, ficaria invisível para sempre. Assim entra na exportação seguinte como lançamento extemporâneo — o CSV leva a data real e o contabilista vê-o. É o que a contabilidade faz |
| **Reabrir um período é proibido** | Permitido com auditoria | `EXPORTED` é terminal no enum e no produto. Reabrir obrigava a inventar `EXPORTED → APPROVED`, contradizer `docs/01-PRODUCT.md` e permitir alterar dados que o contabilista já recebeu. Corrige-se com um documento novo, como já acontece com `APPROVED` |
| Exportação **idempotente por (organização, ano, mês)**, com índice único | Deixar criar duas exportações do mesmo mês | Critério de aceitação do briefing. O segundo `POST` devolve 200 com a exportação que já existe, sem incluir nada de novo |
| CSV gerado no servidor, **gravado no S3**, descarregado por URL pré-assinado | Devolver o CSV no corpo da resposta | Reaproveita o padrão que já existe para os ficheiros dos documentos (`createDownloadUrl` + `Content-Disposition`), evita meter `Authorization` num link de descarga, e o ficheiro fechado fica imutável e re-descarregável |
| CSV: **UTF-8 com BOM, separador `;`, vírgula decimal, `\r\n`** | UTF-8 sem BOM e vírgula como separador | É o que o Excel em português abre com duplo clique sem assistente de importação. Sem BOM, os acentos saem trocados; com `,` a separar, as casas decimais partem as colunas |
| Fechar período e ver dashboard: `FINANCE`, `MANAGER`, `ADMIN` | Só `MANAGER`/`ADMIN` | Quem produz o ficheiro do mês é o assistente financeiro. `EMPLOYEE` nunca vê agregados da organização |
| Gráficos com `@mantine/charts` | Recharts em cru, ou SVG à mão | ADR-0001 escolheu Mantine; `@mantine/charts` já vem com o tema, a tipografia e o modo escuro alinhados |
| Principais fornecedores em **tabela com barra proporcional** (`Table` + `Progress`) | Gráfico de barras horizontais | Mostra o valor exato ao lado da barra, e não depende de uma API do `@mantine/charts` que não foi possível confirmar offline |

ADRs a escrever:
- `docs/adr/0013-read-model-do-dashboard-e-exportacao.md`
- `docs/adr/0014-fecho-de-periodo-e-formato-do-csv.md`

## Por decidir — parar aqui

- Se `@mantine/charts` exigir subir a versão do `@mantine/core` (hoje `^7.17.0`) ou o React
  para a 19: **para e pergunta.** A etapa 08 já bateu nisto com o `react-pdf` (armadilha 6
  do handoff 08). Não subas Mantine nem React por causa de um gráfico.
- Se aparecer qualquer razão para acrescentar uma transição nova a `DocumentStatus`:
  **para e pergunta.** A decisão desta etapa foi que não se reabre um período.
- Se a taxa de automação der 100% em todos os cenários de teste manual: não é bug, é a
  fixture. `docgrid.extraction.stub-fixture=extraction/fixtures/clean-invoice.json` nunca
  precisa de revisão; usa `extraction/fixtures/low-confidence.json` para ver o número mexer.

## Passos de implementação

Por ordem. Cada passo é uma unidade de commit.

---

### 1. Categoria gravada na aprovação

- `backend/src/main/resources/db/migration/V8__document_category.sql` (criar)
  ```sql
  alter table documents add column category varchar(50);
  ```
  Com comentário a dizer porquê: projeção para agregar, à imagem das outras colunas de
  negócio; a verdade continua em `extracted_fields` (ADR-0003).
- `backend/src/main/java/com/docgrid/document/Document.java` (alterar) — campo
  `category` (`@Column(name = "category", length = 50)`), getter, e um único método de
  escrita `void categoriseAs(String category)`. Não acrescentes um `setCategory` público.
- `backend/src/main/java/com/docgrid/document/DocumentApprovalService.java` (alterar) — no
  `approve`, quando `category != null`:
  - `document.categoriseAs(category)`;
  - grava/atualiza a linha CATEGORY em `extracted_fields`: se não existir,
    `ExtractedField.writtenByHuman(document, ExtractedFieldName.CATEGORY, category)`; se
    existir e o valor for diferente, `field.correctTo(category)`;
  - se o valor mudou (inclusive de nulo), `events.save(DocumentEvent.fieldCorrected(...))`.
  - Atenção: `writtenByHuman` já faz `document.addExtractedField`, e a coleção é `LAZY` —
    isto corre dentro do `@Transactional` do `approve`, portanto inicializa sem problema.
- `backend/src/main/java/com/docgrid/document/FieldCorrectionService.java` (alterar) — no
  fim de `correct`, a seguir a `projectInvoiceFields`, projetar também a categoria a partir
  dos campos atuais, para os dois caminhos de escrita não divergirem.
- Testes:
  - `backend/src/test/java/com/docgrid/document/DocumentApprovalServiceTest.java` (alterar)
    — aprovar com categoria grava a coluna, grava o campo HUMAN sem confiança (a constraint
    `ck_extracted_fields_confidence_required` prova-o) e deixa evento; aprovar sem categoria
    não mexe em nada.
  - `backend/src/test/java/com/docgrid/document/FieldCorrectionServiceTest.java` (alterar)
    — corrigir CATEGORY atualiza a projeção em `documents.category`.
- Commit: `feat(document): categoria do documento gravada na aprovação`

### 2. Escrita direta de objetos no armazenamento

- `backend/src/main/java/com/docgrid/storage/StorageService.java` (alterar) — método novo:
  ```java
  /** Escreve um objeto nesta chave. Usa-se para ficheiros gerados pelo servidor (etapa 09). */
  void put(String key, String contentType, byte[] content);
  ```
- `backend/src/main/java/com/docgrid/storage/S3StorageService.java` (alterar) —
  `s3.putObject(PutObjectRequest.builder().bucket(...).key(key).contentType(contentType).build(), RequestBody.fromBytes(content))`.
- Testes: `backend/src/test/java/com/docgrid/storage/S3StorageServiceTest.java` (alterar) —
  `put` seguido de `download` devolve os mesmos bytes (LocalStack, como o resto do ficheiro).
- Commit: `feat(storage): escrita direta de objetos no S3`

### 3. Agregações do dashboard

- `backend/src/main/resources/db/migration/V9__document_aggregation_index.sql` (criar)
  ```sql
  create index idx_documents_org_status_issue_date
      on documents (organization_id, status, issue_date);
  ```
  Serve as agregações por período e a seleção de documentos para exportar; o índice que já
  existe é por `created_at` e não ajuda aqui.
- Pacote novo `backend/src/main/java/com/docgrid/dashboard/` (tudo package-private exceto
  o que o Spring precisa de ver):
  - `package-info.java` — "Read model: agrega documentos para o ecrã de dashboard. Só lê."
  - `DashboardQueries.java` — as quatro consultas com `JdbcClient` (injetado; Boot 3.5
    já o auto-configura). Todas filtram por `organization_id`, sem exceção.
  - `DashboardService.java` — `@Transactional(readOnly = true)`, monta a resposta.
  - `DashboardController.java` — `@RestController`, `@RequestMapping("/api/dashboard")`,
    `@PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")`, parâmetros
    `@RequestParam(required=false) @DateTimeFormat(pattern="yyyy-MM") YearMonth from`
    e `to`. Omissos: os últimos 12 meses, a acabar no mês corrente. A organização vem de
    `CurrentUserProvider`, nunca do pedido.
  - `dto/DashboardResponse.java` e os records dentro dele ou ao lado:
    `PeriodTotals(long documents, BigDecimal net, BigDecimal vat, BigDecimal total)`,
    `Operations(long processedDocuments, BigDecimal automationRate, Long averageReviewSeconds)`,
    `MonthlyTotal(YearMonth month, long documents, BigDecimal net, BigDecimal vat, BigDecimal total)`,
    `CategoryTotal(String category, long documents, BigDecimal total)` — `category` nulo
    quando o documento não tem; o rótulo "Sem categoria" é do frontend, não da API (lição 5
    do handoff 08: a mensagem da API não é a frase do utilizador),
    `SupplierTotal(String taxId, String name, long documents, BigDecimal total)`.
- **Definições que têm de ficar exatamente assim** (são o que os testes provam):
  - *Documentos processados*: `status not in ('UPLOADED','PROCESSING','FAILED')`, por
    `created_at` dentro do período.
  - *Taxa de automação*: desses, a fração que **nunca entrou em `NEEDS_REVIEW` e nunca teve
    um `FIELD_CORRECTED`**:
    ```sql
    count(*) filter (where not exists (
        select 1 from document_events e
        where e.document_id = d.id
          and (e.to_status = 'NEEDS_REVIEW' or e.event_type = 'FIELD_CORRECTED')
    ))::numeric / nullif(count(*), 0)
    ```
    `null` quando não há documentos processados — não se mostra 0% nem 100% sobre nada.
  - *Tempo médio de revisão*: média, em segundos, entre o primeiro evento
    `to_status = 'NEEDS_REVIEW'` e o primeiro evento seguinte com
    `to_status in ('APPROVED','REJECTED')`. Só conta documentos que já saíram da fila;
    `null` se não houver nenhum.
  - *Dinheiro* (totais, mensal, categoria, fornecedor): `status in ('APPROVED','EXPORTED')`
    e `issue_date` dentro do período. `document_events` não tem `organization_id` — junta
    sempre a `documents`.
  - *Principais fornecedores*: `group by supplier_tax_id`, nome de
    `left join suppliers s on s.organization_id = d.organization_id and s.tax_id = d.supplier_tax_id`,
    com `coalesce(s.name, d.supplier_tax_id)`, `order by total desc limit 5`.
- Testes:
  - `backend/src/test/java/com/docgrid/dashboard/DashboardQueriesTest.java` — dados
    conhecidos, números exatos: 4 documentos, 1 que passou pela revisão ⇒ taxa 0,75;
    tempo médio com dois documentos revistos; totais por mês, categoria e fornecedor;
    e um documento de **outra organização** que não pode aparecer em nenhum número.
  - `backend/src/test/java/com/docgrid/dashboard/DashboardControllerTest.java` — anónimo
    401, `EMPLOYEE` 403, `FINANCE` 200 (padrão de `DocumentControllerTest`, com
    `AuthFixtures` e `JwtTestSupport`).
  - `backend/src/test/java/com/docgrid/dashboard/DashboardPerformanceTest.java` — insere
    5000 documentos por `JdbcTemplate` em lote, faz uma chamada de aquecimento, e afirma
    que a segunda demora menos de 1000 ms. Regista o tempo medido com `log.info` para o
    handoff o poder citar.
- Commit: `feat(api): agregações do dashboard com taxa de automação`

### 4. Exportação mensal e fecho de período

- `backend/src/main/resources/db/migration/V10__exports.sql` (criar)
  ```sql
  create table exports (
      id              uuid          primary key,
      organization_id uuid          not null references organizations (id),
      period_year     smallint      not null,
      period_month    smallint      not null,
      storage_key     varchar(500)  not null,
      document_count  integer       not null,
      net_total       numeric(14, 2) not null,
      vat_total       numeric(14, 2) not null,
      total           numeric(14, 2) not null,
      created_by      uuid          not null references users (id),
      created_at      timestamptz   not null,
      updated_at      timestamptz   not null,
      constraint ck_exports_period_month check (period_month between 1 and 12),
      constraint ck_exports_document_count check (document_count >= 0)
  );
  -- Um período fecha-se uma vez. É esta constraint que torna a exportação idempotente.
  create unique index uq_exports_org_period on exports (organization_id, period_year, period_month);

  -- Que exportação levou este documento. Um documento entra numa exportação e só numa.
  alter table documents add column export_id uuid references exports (id);
  create index idx_documents_export on documents (export_id) where export_id is not null;
  ```
- `backend/src/main/java/com/docgrid/document/DocumentExportService.java` (criar, **público**
  — é a porta por onde `export` escreve):
  ```java
  @Transactional
  public int markExported(UUID organizationId, UUID exportId, List<UUID> documentIds, Actor actor)
  ```
  Para cada documento: `events.save(document.transitionTo(EXPORTED, actor, "Exportação %s"))`
  e `document.markExported(exportId)` (método novo em `Document.java`, a escrever a coluna).
- Pacote `backend/src/main/java/com/docgrid/export/`:
  - `Export.java` — entidade, `extends BaseEntity`, package-private.
  - `ExportRepository.java` — `Optional<Export> findByOrganizationIdAndPeriodYearAndPeriodMonth(...)`,
    `List<Export> findByOrganizationIdAndPeriodYearOrderByPeriodMonthDesc(...)`.
  - `ExportQueries.java` — `JdbcClient`. Seleciona as linhas do CSV:
    ```sql
    select d.id, d.issue_date, d.invoice_number, d.supplier_tax_id,
           coalesce(f.value_text, s.name, d.supplier_tax_id) as supplier_name,
           d.net_amount, d.vat_rate, d.vat_amount, d.total_amount,
           d.category, d.currency, d.original_filename
      from documents d
      left join extracted_fields f on f.document_id = d.id and f.field_name = 'SUPPLIER_NAME'
      left join suppliers s on s.organization_id = d.organization_id and s.tax_id = d.supplier_tax_id
     where d.organization_id = :organizationId
       and d.status = 'APPROVED'
       and d.export_id is null
       and d.issue_date is not null
       and d.issue_date < :endExclusive
     order by d.issue_date, d.invoice_number
    ```
    E uma consulta à parte que conta os `APPROVED` **sem `issue_date`** — não entram no
    ficheiro, mas a resposta diz quantos ficaram de fora. Sinaliza, não adivinha.
  - `ExportCsvWriter.java` — função pura `byte[] write(List<ExportRow> rows)`:
    BOM `﻿`, separador `;`, fim de linha `\r\n`, datas `dd-MM-yyyy`, montantes com
    vírgula decimal e **sem separador de milhares**, aspas só quando o valor tem `;`, `"`,
    `\r` ou `\n` (aspas internas duplicadas, RFC 4180).
    Cabeçalho, por esta ordem:
    `Data;Nº da fatura;Fornecedor;NIF;Base tributável;Taxa IVA;IVA;Total;Categoria;Moeda;Ficheiro`
  - `ExportService.java` — `create(YearMonth period)`:
    1. recusa período futuro (`period` depois do mês corrente) com `PeriodNotClosableException`
       (409, `extends DomainException`);
    2. se já existe exportação para o período, devolve-a tal como está — nada é gerado,
       nada muda de estado;
    3. `UUID exportId = UUID.randomUUID()`, gera o CSV, `storage.put("exports/%s/%s/%s.csv"
       .formatted(organizationId, period, exportId), "text/csv; charset=utf-8", bytes)`;
    4. numa transação: grava `Export` e chama `DocumentExportService.markExported`;
    5. se o passo 4 rebentar com violação da constraint única (dois pedidos ao mesmo tempo),
       relê e devolve a que ganhou.
  - `ExportController.java` — `@RequestMapping("/api/exports")`,
    `@PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")`:
    - `POST /api/exports` com `CreateExportRequest(int year, int month)` → 201 com
      `ExportResponse`, ou **200** se o período já estava fechado;
    - `GET /api/exports?year=2026` → `List<ExportResponse>` (sem paginação: são doze por ano);
    - `GET /api/exports/{id}/file-url` → `ExportFileUrlResponse(String url, Instant expiresAt)`.
      **Não lhe chames `FileUrlResponse`**: já existe um com esse nome em
      `com.docgrid.document.dto` e o springdoc dá nome aos esquemas pelo nome simples da
      classe — dois `FileUrlResponse` colidem no OpenAPI.
  - `dto/ExportResponse.java` — id, ano, mês, contagem, totais, `createdAt`,
    `documentsWithoutDate` (os que ficaram de fora).
- Testes:
  - `backend/src/test/java/com/docgrid/export/ExportCsvWriterTest.java` — unitário, sem
    Spring: primeiros três bytes `EF BB BF`; `1234,56` e não `1234.56` nem `1.234,56`;
    `Cantina do Zé, Lda.` sai sem aspas (tem vírgula, não `;`); um nome com `;` sai entre
    aspas; um nome com `"` sai com as aspas duplicadas; acentos intactos ao reler em UTF-8.
  - `backend/src/test/java/com/docgrid/export/ExportServiceTest.java` — exportar duas vezes
    o mesmo mês devolve a mesma exportação, não duplica documentos e não cria segundo
    ficheiro; os documentos incluídos ficam `EXPORTED` com `export_id` preenchido e evento
    em `document_events`; um documento aprovado de um mês anterior ainda por exportar entra
    na exportação seguinte; um documento `NEEDS_REVIEW` não entra; um `APPROVED` sem
    `issue_date` não entra e é contado em `documentsWithoutDate`; período futuro dá 409.
  - `backend/src/test/java/com/docgrid/export/ExportControllerTest.java` — anónimo 401,
    `EMPLOYEE` 403, `FINANCE` 201 e depois 200 no mesmo mês; isolamento por organização
    (uma organização não vê nem descarrega a exportação da outra).
- Commit: `feat(export): exportação mensal em CSV com fecho de período`

### 5. Dashboard no frontend

- `frontend/package.json` — instalar `@mantine/charts` alinhado com o `@mantine/core`
  instalado (`^7.17.0`) e `recharts` na linha 2 (`recharts@^2.15.0`). **Confirma os
  `peerDependencies` antes de instalar** e corre `npm ls recharts react` para ver que nada
  puxou React 19 (armadilha 6 do handoff 08). Importa `@mantine/charts/styles.css` onde já
  se importam os estilos do Mantine (`frontend/src/main.tsx`).
- `frontend/src/format.ts` (criar) — `formatEuro`, `formatPercent`, `formatDate`,
  `formatDuration`, todos em `pt-PT`. Move para aqui os dois formatadores que estão a viver
  dentro de `frontend/src/features/documents/DocumentsTable.tsx:8-9` e passa a tabela a
  importá-los: com o dashboard seriam três cópias do mesmo `Intl.NumberFormat`.
- `frontend/src/api/types.ts` (alterar) — os tipos da resposta do dashboard e das
  exportações, a espelhar os records do backend. `automationRate` e `averageReviewSeconds`
  são `number | null`; `category` é `string | null`.
- `frontend/src/api/dashboard.ts` (criar) — `getDashboard(from, to)` com `apiFetch`.
- `frontend/src/features/dashboard/` (criar):
  - `PeriodFilter.tsx` — dois `TextInput type="month"`, ao jeito de
    `frontend/src/features/documents/DocumentFilters.tsx` (que usa `type="date"`; não
    acrescentes `@mantine/dates`).
  - `KpiRow.tsx` — quatro cartões: Documentos processados · **Taxa de automação** ·
    Tempo médio de revisão · IVA do período. A taxa de automação é o número da etapa:
    cartão maior, cor primária, e uma linha por baixo a dizer o que significa
    ("passaram sem revisão humana"). Com `automationRate` a `null`, mostra "—" e
    "ainda sem documentos processados" — nunca 0%.
  - `MonthlyChart.tsx` — `BarChart` do `@mantine/charts` com o total por mês.
  - `CategoryDonut.tsx` — `DonutChart`; a fatia de `category === null` rotula-se
    "Sem categoria" aqui, no frontend.
  - `TopSuppliers.tsx` — `Table` com fornecedor, nº de documentos e total, mais um
    `Progress` proporcional ao maior valor.
  - `dashboardChartData.ts` — as funções puras que passam a resposta da API para o formato
    dos gráficos (rótulo do mês em pt, ordenação, agrupar a cauda em "Outros" na dona).
  - `useDashboard.ts` — `useQuery`, `placeholderData: keepPreviousData` (mudar o período
    não deve piscar o ecrã), ao jeito de `ReviewQueuePage.tsx`.
  - `DashboardPage.tsx` — junta tudo, com estados de carregamento, erro (com repetir) e
    vazio.
- `frontend/src/App.tsx` (alterar) — rota `/dashboard` com `RequireAuth` + `AppShellLayout`
  + `RequireRole allowed={REVIEW_ROLES}`.
- `frontend/src/layout/NavLinks.tsx` (alterar) — entrada "Dashboard" (`IconChartBar`),
  visível aos mesmos papéis.
- Testes:
  - `frontend/src/format.test.ts` — euro, percentagem e duração em pt-PT.
  - `frontend/src/features/dashboard/dashboardChartData.test.ts` — as funções puras.
  - `frontend/src/features/dashboard/DashboardPage.test.tsx` — com MSW: a taxa de automação
    aparece e está certa; o estado vazio mostra "—"; um erro mostra o botão de repetir.
    **Não afirmes nada sobre o SVG dos gráficos** — o `ResponsiveContainer` do Recharts não
    tem dimensões em jsdom e desenha vazio. Dá altura fixa aos gráficos e testa os números.
- Commit: `feat(frontend): dashboard com a taxa de automação em destaque`

### 6. Fecho de período e descarga no frontend

- `frontend/src/api/exports.ts` (criar) — `listExports(year)`, `createExport(year, month)`,
  `getExportFileUrl(id)`.
- `frontend/src/features/exports/` (criar):
  - `ExportsPage.tsx` — seletor de mês, botão "Fechar período e exportar", e a lista das
    exportações do ano com data, nº de documentos, total e botão de descarga.
  - `ConfirmCloseModal.tsx` — o fecho é irreversível e tem de o dizer por palavras: quantos
    documentos vão ser incluídos e que depois disso não se mexem mais. Modal do Mantine,
    ao jeito de `frontend/src/features/review/RejectModal.tsx`.
  - `useExports.ts` — `useQuery` + `useMutation`, a invalidar a lista depois de exportar.
  - Descarga: `getExportFileUrl(id)` e depois
    `window.open(url, "_blank", "noopener,noreferrer")` — o URL do S3 já vem com
    `Content-Disposition: attachment`, portanto não é preciso truques com `blob`.
  - Quando a resposta do `POST` vier com 200 (período já fechado), mostra uma notificação a
    dizer que o período já estava fechado, em vez de fingir que exportou agora.
- `frontend/src/App.tsx` e `frontend/src/layout/NavLinks.tsx` (alterar) — rota `/exports`
  e entrada "Exportações" (`IconFileExport`), mesmos papéis.
- Testes: `frontend/src/features/exports/ExportsPage.test.tsx` — exportar chama o `POST` e
  mostra a linha nova; exportar um mês já fechado mostra o aviso e não duplica linhas;
  o botão de descarga chama `window.open` com o URL que a API devolveu (`vi.spyOn`).
- Commit: `feat(frontend): fecho de período e descarga do CSV`

### 7. Documentação

- `docs/adr/0013-read-model-do-dashboard-e-exportacao.md` — agregar em SQL e não em Java;
  pacote `dashboard` como read model; a regra "lê-se por SQL, escreve-se pelo pacote dono".
- `docs/adr/0014-fecho-de-periodo-e-formato-do-csv.md` — período pela `issue_date`,
  lançamentos extemporâneos, não se reabre, idempotência pelo índice único, e o formato do
  CSV para o Excel português (BOM, `;`, vírgula decimal).
- `docs/03-CONVENTIONS.md` — acrescentar `dashboard/` à árvore de pacotes do backend.
- `docs/01-PRODUCT.md` — secção curta sobre exportação e fecho de período, com a regra do
  lançamento extemporâneo e a categoria a ser gravada na aprovação.
- `README.md` — SAF-T como evolução possível (o briefing pede que fique mencionado).
- Commit: `docs(adr): read model do dashboard e fecho de período`

## Critérios de aceitação

```bash
# backend — 272 testes antes desta etapa, todos verdes mais os novos
npm test

# frontend
cd frontend
npx tsc -b --noEmit
npx eslint .
npx vitest run
npm run build
```

Manual, com `npm run up` na raiz e `cd frontend && npm run dev`:

```bash
# com um token de FINANCE
curl -s "http://localhost:8080/api/dashboard?from=2026-01&to=2026-09" -H "Authorization: Bearer $TOKEN" | jq
curl -s -X POST http://localhost:8080/api/exports -H "Authorization: Bearer $TOKEN" \
     -H "content-type: application/json" -d '{"year":2026,"month":8}' -i | head -1   # HTTP/1.1 201
curl -s -X POST http://localhost:8080/api/exports -H "Authorization: Bearer $TOKEN" \
     -H "content-type: application/json" -d '{"year":2026,"month":8}' -i | head -1   # HTTP/1.1 200
```

- [ ] O dashboard carrega em menos de um segundo com 5000 documentos
      (`DashboardPerformanceTest` regista o tempo medido no log)
- [ ] O CSV abre no Excel com acentos certos e vírgulas decimais — **abre mesmo o ficheiro
      no Excel**, não basta o teste dos bytes
- [ ] Exportar duas vezes o mesmo mês não duplica documentos: segunda chamada devolve 200,
      a lista continua com uma linha, `select count(*) from exports` não muda
- [ ] A taxa de automação está visível no topo do dashboard e bate certo com os documentos
      que passaram pela fila de revisão
- [ ] `EMPLOYEE` recebe 403 em `/api/dashboard` e em `/api/exports`

## Fora de âmbito

- Integração real com software de contabilidade → não pertence a nenhuma etapa; está
  explicitamente fora em `docs/01-PRODUCT.md`
- SAF-T completo → só menção no `README.md`
- Reabrir um período fechado → decidido que não existe
- Exportação automática agendada e exportação em XML → não estão no roteiro
- Dashboard do `EMPLOYEE` com as suas próprias despesas → não está no roteiro
- Ecrã da DLQ → etapa 10 · `npm run seed` e o GIF do README → etapa 12
- O GIF do ecrã de revisão que ficou por fazer na etapa 08 → continua a pertencer à 12

## Riscos conhecidos

- **`@mantine/charts` e o React.** Se a instalação falhar com `ERESOLVE`, para e pergunta;
  não subas Mantine nem React (armadilha 6 do handoff 08).
- **Recharts em jsdom.** O `ResponsiveContainer` mede 0×0 e não desenha. Não escrevas
  testes sobre o SVG; testa as funções de dados e os números do ecrã.
- **Teste de desempenho instável.** Mede a segunda chamada, não a primeira: a primeira paga
  o arranque do JVM e o plano de consulta. Se mesmo assim oscilar, regista o tempo e não
  transformes o teste num gerador de falsos negativos — diz-me.
- **Ficheiro órfão no S3.** A ordem é: gerar e gravar o CSV, depois escrever na base de
  dados. Se a transação falhar depois do upload, fica um objeto no S3 sem registo — sem
  consequência funcional. O contrário (registo a apontar para um ficheiro que não existe)
  seria pior e é por isso que a ordem é esta.
- **Coleção `LAZY` fora de transação.** Se acrescentares alguma coleção nova a um agregado
  servido por um `GET`, testa-a **sem** `@Transactional` na classe de teste — foi assim que
  a etapa 08 apanhou a `LazyInitializationException`
  (`backend/src/test/java/com/docgrid/document/DocumentDetailTest.java` existe para isso).
- **Ordem dos handlers do MSW.** `/api/exports/:id/file-url` e `/api/exports` no mesmo
  `server.use()`: regista primeiro o mais específico (armadilha 2 do handoff 08).
