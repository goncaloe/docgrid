# Handoff — Etapa 04: Extração de dados

**Data:** 2026-09-10 · **Sessão:** #5 · **Estado:** completa

## Contexto de arranque

A etapa 03 deixou o worker a extrair com um `StubExtractor` de constantes hard-coded, que
escrevia 7 campos com confiança mas sem bounding box (as colunas nem existiam). Esta
sessão fechou a extração a sério: geometria persistida, stub dirigido por fixtures, o
Textract atrás da mesma interface, e a distinção permanente/transitório no worker.

## O que ficou feito

### Geometria — `com.docgrid.extraction` + `com.docgrid.document`

- **`FieldGeometry`** (novo) — record `page` + polígono de `Point(x, y)` normalizado
  0–1. Validação no construtor compacto: página ≥ 1, polígono com ≥ 3 pontos, coordenadas
  em 0.0–1.0. `serializedPolygon()` produz o formato guardado em BD:
  `[[0.1, 0.1], [0.4, 0.1], [0.4, 0.2]]`.
- **`ExtractionResult`** (alterado) — terceira componente
  `Map<ExtractedFieldName, FieldGeometry>`, com invariante estrutural no construtor
  compacto: `geometries.keySet().containsAll(confidences.keySet())`. Um campo com
  confiança **tem sempre** geometria.
- **`ExtractedField`** (alterado) — campos `page` (`Integer`) e `boundingBox` (`String`
  com `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`). `readByMachine`
  recebe `FieldGeometry` obrigatório; `writtenByHuman` passa null;
  **`correctTo` limpa a geometria** — quem corrigiu escreveu um valor novo, sem
  coordenadas. Getters `getPage()`/`getBoundingBox()` novos.
- **`V6__extracted_fields_geometry.sql`** (nova migração) — `page int`, `bounding_box
  jsonb`, e duas constraints novas:
  - `ck_extracted_fields_bbox_source` — a simetria de
    `ck_extracted_fields_confidence_required`: `AI ⇒ bbox e page não nulos`, `HUMAN ⇒
    nulos`.
  - `ck_extracted_fields_bbox_json` — `bounding_box` é sempre array jsonb.
- **`DocumentProcessor.writeExtractedFields`** (alterado) — passa
  `result.geometries().get(fieldName)` ao `readByMachine`.
- **`InvoiceFields`** (alterado) — `supplierName` como **primeira** componente. Sem
  coluna na projeção de `documents` (ADR 0003 mantém-se): não serve para procurar nem
  agregar; vive só em `extracted_fields` com confiança e geometria.

### Stub por fixtures — `com.docgrid.extraction`

- **`ExtractionProperties`** (novo) — `@ConfigurationProperties("docgrid.extraction")`,
  record com `stubFixture` e `Textract(region, endpoint, accessKey, secretKey,
  apiTimeout)` + `hasCustomEndpoint()`, copiando o desenho de `QueueProperties`.
  (`@ConfigurationPropertiesScan` no arranque não precisa de registo extra.)
- **`StubExtractor`** (reescrito) — `@Profile({"local", "test"})`; carrega a fixture
  `docgrid.extraction.stub-fixture` do classpath **no construtor** e guarda o resultado:
  um caminho errado falha na subida, não em runtime. Chaves camelCase de `InvoiceFields` +
  `vatRate`; montantes como strings `"100.00"`; cada entrada tem `confidence`, `page`,
  `polygon`.
- **Fixtures** em `backend/src/main/resources/extraction/fixtures/` —
  `clean-invoice.json` (8 campos, confianças 0.94–0.99),
  `low-confidence.json` (mesmos valores, confianças 0.30–0.80 — a "foto tremida"),
  `missing-field.json` (sem `invoiceNumber` nem `vatAmount`/`vatRate`; `total = net`,
  para não violar a aritmética que a etapa 05 vai verificar). **Em `src/main/resources`,
  não em `src/test`** — ver Decisões.
- **`StubExtractorTest`** (novo, unitário sem Spring) — fatura limpa: 8 campos, confiança
  ≥ 0.85, geometria em todos; foto tremida: tudo < 0.85; campo em falta: saído dos dois
  mapas; montantes com escala 2; fixture em falta falha no construtor.

### Textract — `com.docgrid.extraction` (só perfil `aws`)

- **`TextractNormalizer`** (novo, package-private, puro) — `fromResponse(
  AnalyzeExpenseResponse)`. Mapeia os tipos do sumário (`VENDOR_NAME` → `supplierName`,
  `VENDOR_TAX_ID`, `INVOICE_RECEIPT_ID`, `INVOICE_RECEIPT_DATE`, `SUBTOTAL` → `netAmount`,
  `TAX` → `vatAmount`, `TAX_PCT`/`VAT_RATE` → `vatRate`, `TOTAL` → `totalAmount`) e
  normaliza: datas ISO + três de dia primeiro (`dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yyyy`);
  montantes pela **regra do último separador** (`1.234,56`, `1,234.56`, `€ 1 234,56`,
  `23%`) → `BigDecimal` escala 2; NIF sem espaços e sem prefixo `PT`; trim em tudo.
  Dois candidatos ao mesmo campo: maior confiança, empate → primeiro da resposta, com log
  INFO do descartado. Campo ausente → null e sem entrada nos mapas — **sem heurísticas**.
  Geometria do `valueDetection().geometry().polygon()` (já normalizada) + `pageNumber()`.
- **`TextractExtractor`** (novo, `@Component @Profile("aws")`) — `analyzeExpense` com
  `Document.builder().bytes(SdkBytes...)` (a classe `Document` do pacote textract; a
  colisão com `com.docgrid.document.Document` resolve-se no import). Normaliza e traduz
  erros via `TextractError`.
- **`TextractError`** (novo, package-private) — `UnsupportedDocumentException` /
  `BadDocumentException` / `InvalidParameterException` → `UnreadableDocumentException`
  (permanente); todo o resto → `RuntimeException("Serviço Textract indisponível", t)`
  (transitório → o SQS repete). Escrito com `instanceof` e não com `switch` de padrões —
  o palantir-java-format não engola esse switch (ver Armadilhas).
- **`TextractConfig`** (novo, `@Profile("aws")`) — bean `TextractClient` copiando
  `SqsConfig`: região, credenciais (estáticas só com endpoint; senão cadeia por omissão),
  `endpointOverride`, e `apiCallTimeout` da propriedade (60s).
- **`UnreadableDocumentException`** (novo) — `extends DomainException`.
- **`DocumentProcessor`** (alterado) — catch
  `UnreadableDocumentException` **antes** do genérico: `fail(...)` imediato, sem esperar
  pelas três tentativas do SQS. O catch `RuntimeException` mantém-se transitório.
- **`backend/pom.xml`** — `software.amazon.awssdk:textract` (mesmo BOM, 2.54.13).
- **`application-aws.yml`** — bloco `docgrid.extraction.textract` (região da variável,
  endpoint e credenciais vazios).
- **Respostas gravadas** em `backend/src/test/resources/textract/` — `clean-invoice`,
  `comma-amounts`, `date-formats` (três páginas; a data vencedora vem da página 2),
  `duplicate-field` (TOTAL com confianças distintas e INVOICE_NUMBER empatado),
  `missing-field`, `pt-nif-prefix`. Carregadas pelo helper de teste `TextractFixtures`
  (DTO espelho com os nomes do fio → builders do SDK) — o normalizador recebe
  `AnalyzeExpenseResponse` de verdade, como em produção.

### ADR

- `docs/adr/0009-analyze-expense-e-geometria-dos-campos.md` — AnalyzeExpense (contra
  `AnalyzeDocument` com queries), geometria em `jsonb` (contra colunas float8 e tabela
  própria), candidatos duplicados, `supplier_name` entra / `currency` e `category` não,
  taxa só por campo próprio, erros em duas categorias, perfis.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **`AnalyzeExpense`** | `AnalyzeDocument` com queries | Devolve campos tipados com `ValueDetection` que já traz confiança e geometria normalizada. Com queries teria de ser heurística nossa para associar texto a campo — o trabalho que o serviço pago existe para fazer. API síncrona, sem jobs. ADR 0009. |
| **`bounding_box jsonb` + `page int` na linha do campo** | Colunas float8 (retângulo); tabela própria de geometrias | O Textract devolve polígono, não retângulo; normalizado 0–1 o frontend sobrepõe sem dimensões de página. Tabela própria = junção por campo sem ganho. ADR 0009. |
| **Maior confiança vence; empate → primeiro** | Fusão de valores | Combinar dois números numa fatura é inventar dados. A ordem da resposta é o desempate estável, com log do descartado. |
| **Fixtures em `src/main/resources`** (desvio ao briefing) | `src/test/resources`, como o briefing dizia | O stub também corre no perfil `local` (`npm run up`) e o classpath de main não vê test resources. Nota no ADR 0009. |
| **`supplier_name` entra; `currency` e `category` não** | Extraer tudo | Currency tem default `'EUR'` e o Textract não a devolve de forma fiável; categoria é sugestão por histórico (etapa 05+). A projeção de `documents` não ganha coluna — ADR 0003. |
| **`vatRate` só por campo próprio (`TAX_PCT`)** | Derivar `vat/net` | Derivação é inferência — trabalho da validação da etapa 05, que assim ainda tem o que verificar. |
| **Ilegível = permanente (fail à primeira); serviço em baixo = transitório** | Retry uniforme para tudo | Repetir a chamada não torna a foto menos tremida. Falha do serviço é do SQS que já sabe repetir. ADR 0009. |
| **DTO espelho + builders do SDK nos testes** | `JsonProtocolUnmarshaller` do SDK; DTO próprio sem builders | O unmarshaller é interno (`protocols.json.internal.unmarshall`) e acoplado ao protocolo. Com o espelho, o normalizador recebe o tipo real do SDK — o caminho de produção é o mesmo. |
| **Geometria sintética no commit 1 do stub** | Fazer o stub final logo no commit 1 | O histórico fica em três commits que compilam isoladamente (verificado com worktrees); a geometria sintética cumpre a invariante até chegar a fixtures. |

ADRs escritos: `docs/adr/0009-analyze-expense-e-geometria-dos-campos.md`

## Como verificar

### Testes e formatação

Precisa do Docker Desktop (Testcontainers: Postgres + 2 LocalStacks — lento, normal).

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 167, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

De 148 para 167: **19 testes novos** — 6 em `StubExtractorTest` (unitário), 8 em
`TextractNormalizerTest` (unitário, sobre respostas gravadas), 2 em `TextractErrorTest`
(unitário), 3 em `ExtractedFieldRepositoryTest` (constraints em SQL). Continuam a aparecer
as linhas `ERROR ... duplicate key` esperadas dos testes de restrição (etapa 03).

### Fluxo completo por `curl` — geometria incluída

**`npm run down` antes do primeiro arranque com a V6** (apaga o volume; a constraint nova
viola dados da etapa 03 — ver Armadilhas).

```bash
npm run up
# noutro terminal:
printf 'fatura de teste' > f.pdf
RESP=$(curl -s -XPOST localhost:8080/api/documents/upload-url \
  -H 'content-type: application/json' \
  -d "{\"filename\":\"f.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":$(wc -c < f.pdf)}")
DOC=$(echo "$RESP" | grep -o '"documentId":"[^"]*"' | cut -d'"' -f4)
URL=$(echo "$RESP" | grep -o '"uploadUrl":"[^"]*"' | cut -d'"' -f4)
curl -s -o /dev/null -w '%{http_code}\n' --upload-file f.pdf -H 'content-type: application/pdf' "$URL"
#  -> 200; ao fim de 1-2 s:

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select status from documents where id='$DOC'"
#  -> EXTRACTED

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select field_name, confidence, page, jsonb_typeof(bounding_box) \
   from extracted_fields where document_id='$DOC' order by field_name"
#  -> 8 linhas (SUPPLIER_NAME incluído), confidence 0.90-0.99, page 1, bounding_box 'array'

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select bounding_box from extracted_fields \
   where document_id='$DOC' and field_name='TOTAL_AMOUNT'"
#  -> [[0.6, 0.55], [0.9, 0.55], [0.9, 0.59], [0.6, 0.59]]
```

**Executado nesta sessão** com o fluxo acima: documento `EXTRACTED` com as 8 linhas
exatas acima.

### Troca de perfil (critério de aceitação nº 2)

Por `@Profile` exclusivos: `StubExtractor` em `{local, test}`, `TextractExtractor` e o
cliente em `aws`. Nos testes (`@ActiveProfiles("test")`) corre o stub. A prova direta do
perfil `aws` exigiria o Textract real — aceita-se a prova por construção + contexto de
teste verde, como o plano da etapa fixou.

## O que ficou por fazer

Nada em falta bloqueia a etapa 05. Fora de âmbito por decisão:

- **Decidir se o documento está bom** — a etapa 05. Aqui só se extrai e guarda; os
  campos chegam à projeção e a `extracted_fields` com confiança e geometria.
- **Heurísticas para NIF ausente** (regex sobre o texto do documento) — posto de lado de
  propósito: sinalizar para revisão, não adivinhar. Se a etapa 05 quiser outra política,
  é discutir lá.
- **`currency` e `category`** — não extraídos (ver Decisões).
- **`AnalyzeDocument` com queries, linha a linha (line items)** — não entra nesta etapa;
  o sumário chega aos campos do briefing.
- **Perfil `aws` provado contra o serviço real** — exigiria o Textract a pagar. Ficou a
  prova por construção (ADR 0009).

## Armadilhas para a próxima sessão

1. **`npm run down` obrigatório antes do primeiro arranque com a V6.** A constraint
   `ck_extracted_fields_bbox_source` (`AI ⇒ bounding_box not null`) quebra os dados que a
   etapa 03 escreveu (AI sem geometria). A BD local é descartável — `npm run down` apaga
   o volume e a V6 aplica limpa. Uma instância local velha rebenta no arranque do Flyway
   com uma falha de validação da migração.
2. **`JsonProtocolUnmarshaller` do SDK não se usa.** É interno
   (`software.amazon.awssdk.protocols.json.internal.unmarshall`) e não faz parte da API
   pública. O caminho nos testes é o `TextractFixtures`: DTO espelho (campos com os nomes
   exatos da resposta, `ExpenseDocuments`, `SummaryFields`, …) → builders do SDK. Se
   acrescentares tipos novos às fixtures gravadas, atualiza o espelho.
3. **palantir-java-format recusa `switch` com padrões múltiplos (`case A | B | C e ->`).**
   Compila com o javac mas o Spotless falha com `: or -> expected`. O `TextractError`
   usa `instanceof` por isto. Se reintroduzires um switch de padrões, testa o
   `npm run lint` antes de commitares.
4. **Floats do Textract ≠ doubles literais.** `Point` do SDK é `Float`; `0.60f ≠ 0.60`
   em double. Nos testes, compara com `isCloseTo(..., offset(0.001))`, não com
   `startsWith(new Point(...))`. E o `toString()` do `FieldGeometry.Point` usa
   `String.format(Locale.ROOT, ...)` — sem o `Locale.ROOT`, numa JVM com locale PT o
   ponto vira vírgula e o JSON guardado fica inválido.
5. **O formatter (spotless:apply) remove imports "não usados" à primeira falha de
   compilação.** Se uma edição deixa uma classe temporariamente sem compilar, o
   `spotless:apply` pode-lhe comer os imports de vez. Depois de qualquer
   `mvn spotless:apply` num ficheiro mal formado, confere os imports antes de recompilar
   (aconteceu com os imports de credenciais do `TextractConfig` nesta sessão).
6. **A fixture `missing-field.json` é aritmeticamente consistente** (sem IVA; `total =
   net`). Quando acrescentares casos maus ao stub, mantém a convenção da etapa 03: um
   stub que viola as regras que a 05 vai impor confunde os testes de pipeline.
7. **`@ConfigurationPropertiesScan` está no arranque** — um properties record novo basta,
   sem `@EnableConfigurationProperties`. (`ExtractionProperties` segue o padrão de
   `QueueProperties`.)
8. **O `DocumentProcessor` escreve 8 campos, não 7** — `SUPPLIER_NAME` entrou
   (`result.confidences()` dirige a escrita; um campo sem entrada nos mapas não gera
   linha). Testes que afirmem contagens têm de dizer 8.
9. **A extração corre fora de transação, como antes** — a chamada ao Textract (lenta,
   paga) não segura ligação à BD. Não mover para dentro da transação de escrita.

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/extraction/TextractNormalizer.java` — a fronteira
  entre o vocabulário do Textract e o nosso modelo. É aqui que datas, montantes e NIF
  convergem, e onde se mapeiam tipos novos.
- `backend/src/main/java/com/docgrid/extraction/FieldGeometry.java` — o polígono
  normalizado, com validação, e `serializedPolygon()` (o formato em BD).
- `backend/src/main/java/com/docgrid/extraction/StubExtractor.java` — o stub dirigido por
  fixtures; a carga falha cedo.
- `backend/src/main/java/com/docgrid/document/ExtractedField.java` — `page` +
  `boundingBox`, e a simetria AI/HUMAN nos dois construtores e no `correctTo`.
- `backend/src/main/resources/db/migration/V6__extracted_fields_geometry.sql` — colunas
  novas e as duas constraints.
- `backend/src/main/java/com/docgrid/extraction/TextractError.java` — a fronteira
  permanente/transitório.
- `backend/src/main/java/com/docgrid/extraction/ExtractionProperties.java` — fixture do
  stub + ligação ao Textract.
- `backend/src/test/java/com/docgrid/extraction/TextractFixtures.java` — respostas
  gravadas → tipo real do SDK (DTO espelho + builders).
- `backend/src/test/resources/textract/*.json` — as respostas gravadas.
- `docs/adr/0009-analyze-expense-e-geometria-dos-campos.md`

## Commits

```
c6bbd89 feat(extracao): geometria e nome do fornecedor nos campos extraidos
eced3d2 feat(extracao): stub dirigido por fixtures JSON
57e38b2 feat(extracao): Textract AnalyzeExpense e erro permanente de documento ilegivel
```

(A etapa 04 foi escrita num commit único e depois reescrita nestes três com `reset`;
cada um compila isoladamente — verificado com worktrees temporárias — e a árvore final é
idêntica à do commit único original, `871aaa0`, que ficou órfão no reflog.)
