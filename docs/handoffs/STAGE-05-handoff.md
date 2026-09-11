# Handoff — Etapa 05: Motor de validação

**Data:** 2026-09-11 · **Sessão:** #6 · **Estado:** completa

## Contexto de arranque

A etapa 04 deixou a extração completa — campos, confiança e geometria persistidos — mas o
`DocumentProcessor` transitava **sempre** para `EXTRACTED`, sem verificar se o que foi
lido fazia sentido. O scaffold de dados desta etapa já existia desde a etapa 02
(`validation_results`, `ValidationResult`, `ValidationResultRepository`,
`organizations.approval_threshold`, `suppliers` com `usual_category`/`occurrence_count`, e
os dois índices de duplicado em `documents`) — por usar. Esta sessão fechou o motor a
sério: oito regras, integração no worker, e o serviço de aprovação que alimenta o
histórico do fornecedor.

## O que ficou feito

### Motor de validação — `com.docgrid.validation`

- **`ValidationRule`** (novo, interface pública) — `String ruleName()` +
  `Optional<RuleOutcome> evaluate(ValidationContext)`. `Optional.empty()` significa "sem
  dados para se pronunciar" — não escreve linha nenhuma; um dado de negócio em falta já é
  apanhado por `MinConfidenceRule`, sem duplicar o aviso.
- **`ValidationContext`** (novo, record público) — tudo o que as regras precisam,
  pré-calculado por quem chama: `documentId`, `supplierTaxId`, `invoiceNumber`,
  `issueDate`, `netAmount`, `vatAmount`, `vatRate`, `totalAmount`,
  `fieldConfidences` (`Map<ExtractedFieldName, BigDecimal>`), `approvalThreshold`,
  `duplicateInvoiceDocumentId`, `duplicateFileDocumentId`, `supplierUsualCategory`,
  `supplierOccurrenceCount`, e **`today`** — passado explicitamente (não
  `LocalDate.now()` dentro da regra) para `PlausibleDateRule` ser pura e testável sem
  depender do relógio.
- **`RuleOutcome`** / **`ValidationSummary`** (novos, records públicos) — o primeiro é o
  veredito de uma regra (`severity`, `passed`, `message`); o segundo é o único que
  atravessa a fronteira do pacote para `com.docgrid.document`
  (`requiresReview`, `reason` — mensagens das falhas `WARNING`/`ERROR` concatenadas com
  `"; "`).
- **`ValidationEngine`** (novo, `@Service` público) — recebe `List<ValidationRule>`
  injetado pelo Spring (é isto que torna "uma regra nova sem tocar em código existente"
  verdade, não só objetivo), apaga os resultados anteriores
  (`deleteByDocumentId` + **`results.flush()`** — ver Armadilhas), corre todas as regras,
  grava uma `ValidationResult` por `Optional` presente, devolve o `ValidationSummary`.
- **As oito regras** (novas, `@Component` package-private, `@Order(1..8)`):
  - `ArithmeticRule` — `|net + vat - total| ≤ 0.02`.
  - `VatRateRule` — `vat/net*100` a ≤0.5 pontos percentuais de 6/13/23 (tolerância não
    fixada no produto; adotada nesta etapa).
  - `TaxIdRule` — dígito de controlo módulo 11, com normalização defensiva (maiúsculas,
    sem espaços, sem prefixo `PT`) mesmo sabendo que o `TextractNormalizer` já normaliza
    na origem.
  - `DuplicateRule` — interpreta `duplicateInvoiceDocumentId`/`duplicateFileDocumentId`
    já calculados pelo `DocumentProcessor`; mensagens diferentes, combinadas se os dois
    baterem.
  - `MinConfidenceRule` — os 7 campos fiscais (exclui `SUPPLIER_NAME`, `CURRENCY`,
    `CATEGORY`) acima de 0.85; um campo ausente do mapa conta como falha.
  - `PlausibleDateRule` — `today-24meses ≤ issueDate ≤ today+7dias`.
  - `ApprovalThresholdRule` — sempre `INFO`, `passed=true`; só fala quando o total excede
    o limite da organização.
  - `CategorySuggestionRule` — sempre `INFO`, `passed=true`; só fala com 5+ aprovações
    seguidas na mesma categoria.

### Integração — `com.docgrid.document`

- **`DocumentProcessor`** (alterado) — construtor recebe `ValidationEngine`,
  `ApprovalThresholdProvider`, `SupplierHistoryProvider`. `complete(...)` monta o
  `ValidationContext` (`buildValidationContext`, novo, privado) depois de
  `projectInvoiceFields` — as duas consultas de duplicado
  (`findDuplicateInvoiceDocumentId`, `findDuplicateFileDocumentId`, novos, privados) usam
  `DocumentRepository.findByOrganizationIdAndSupplierTaxIdAndInvoiceNumber`/
  `findByOrganizationIdAndFileHash`, já existentes desde a etapa 02. Invoice-duplicado só
  conta documentos `APPROVED`; ficheiro-duplicado exclui só `REJECTED`. Decide
  `EXTRACTED` vs `NEEDS_REVIEW` a partir de `ValidationSummary.requiresReview()`, escreve
  `summary.reason()` no `DocumentEvent`.
- **`DocumentApprovalService`** (novo, `@Service` público,
  `approve(UUID documentId, Actor actor, String category)`) — transita para `APPROVED`;
  se `category != null` e o documento tem `supplierTaxId`, vai buscar o nome do
  fornecedor a `extracted_fields` (com o próprio NIF como rede de segurança) e chama
  `SupplierApprovalRecorder.recordApproval(...)`. **Sem endpoint REST nesta etapa** — ver
  ADR 0010.

### Portos públicos novos

- **`com.docgrid.auth.ApprovalThresholdProvider`** — `BigDecimal
  approvalThresholdFor(UUID organizationId)`, implementado por
  `OrganizationApprovalThresholdProvider` sobre `OrganizationRepository`. Mesmo padrão de
  `CurrentUserProvider`.
- **`com.docgrid.supplier.SupplierHistoryProvider`** (leitura) — `SupplierHistory
  historyFor(organizationId, taxId)`, `SupplierHistory` é um record público
  (`usualCategory`, `occurrenceCount`) com `unknown()` estático.
- **`com.docgrid.supplier.SupplierApprovalRecorder`** (escrita) — `void
  recordApproval(organizationId, taxId, supplierName, category)`. Uma única
  implementação, `SupplierService`, cumpre os dois portos.
- **`Supplier.recordApproval(String category)`** (alterado) — os dois métodos primitivos
  sem chamador da etapa 02 (`recordOccurrence`, `rememberUsualCategory`) tornam-se um só:
  mesma categoria incrementa `occurrenceCount`, categoria diferente reinicia a 1.
  `occurrenceCount` é o comprimento da sequência **consecutiva**, não um total histórico.

### Fixtures

- **Corrigida a data fixa `2026-09-15`** → `2026-08-20` em `clean-invoice.json`,
  `low-confidence.json`, `missing-field.json` — a antiga estava a 4 dias da borda da
  janela de plausibilidade (`hoje+7 dias`) e ia partir os testes de pipeline
  silenciosamente dentro de uma semana.
- **`bad-arithmetic.json`** (nova) — cópia de `clean-invoice.json` com `totalAmount`
  errado (150.00 em vez de 123.00), isola o teste "IVA que não bate certo".
- **`duplicate-check.json`** (nova) — cópia de `clean-invoice.json`, usada só para dar aos
  testes de duplicado um contexto Spring (e Postgres) próprio — ver Armadilhas.

### ADR

- `docs/adr/0010-motor-de-validacao-e-servico-de-aprovacao.md` — desenho do motor
  (interface + descoberta pelo Spring + onde vive a decisão `EXTRACTED`/`NEEDS_REVIEW`),
  duplicado como regra única com dois critérios, `DocumentApprovalService` sem REST nesta
  etapa.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **Duplicado: uma regra, dois critérios** (NIF+número contra `APPROVED`; hash de ficheiro contra não-`REJECTED`) | Regras separadas; só um critério | Os dois índices já existiam desde a etapa 02 para isto. Uma linha só em `validation_results` evita duplicar o conceito de negócio "já processámos isto". |
| **Limiares fixos em código** (confiança 0.85, tolerâncias de IVA 0.5pp e data) | Configuráveis por organização | Sem requisito atual que o justifique; exigiria tabela nova. |
| **`DocumentApprovalService` sem endpoint REST nesta etapa** | Esperar pela etapa 06 | O briefing lista "atualização de suppliers a cada documento aprovado" no âmbito da 05, não da 06. "Verifica-se por API" leu-se como API Java, testada diretamente — a etapa 06 só liga o controller. |
| **Confiança mínima: os 7 campos fiscais** (exclui `SUPPLIER_NAME`, `CURRENCY`, `CATEGORY`) | Todos os 8 campos extraídos | Só os 7 entram nas outras regras de negócio e na exportação; o nome do fornecedor é informativo. |
| **`occurrenceCount` é sequência consecutiva, não total histórico** | Guardar histórico completo por categoria | O schema só tem `usual_category` + `occurrence_count` (2 colunas); um histórico completo é mais tabela do que o problema pede agora. Consequência aceite no ADR 0010: um fornecedor que alterna categorias nunca acumula sequência. |
| **`today` no `ValidationContext`, não `LocalDate.now()` dentro da regra** | Regra chama o relógio diretamente | Sem isto, `PlausibleDateRuleTest` não seria determinístico — a janela muda todos os dias. |
| **Duas classes de teste `@SpringBootTest` extra isoladas por `@TestPropertySource`** (`DocumentProcessorValidationTest`, `DocumentProcessorDuplicateTest`) | Meter tudo em `DocumentProcessorTest` | `PostgresContainerConfiguration` partilha o Postgres entre classes com a mesma configuração (é a decisão documentada no próprio ficheiro). Um teste que aprova um documento com o NIF+número da fixture "canónica" (`clean-invoice.json`) deixa esse documento `APPROVED` a viver na base de dados partilhada — qualquer teste posterior que reprocesse a mesma fixture nesse contexto passa a ver um duplicado falso. Ver Armadilhas. |

ADRs escritos: `docs/adr/0010-motor-de-validacao-e-servico-de-aprovacao.md`

## Como verificar

### Testes e formatação

Precisa do Docker Desktop (Testcontainers: Postgres + 2 LocalStacks).

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 230, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

De 167 para 230: **63 testes novos** — unitários puros por regra (incluindo 15 casos de
`TaxIdRuleTest`), `ValidationEngineTest` (persistência, `@RepositoryTest`),
`OrganizationApprovalThresholdProviderTest`, `SupplierTest` (streak), testes de pipeline
completos (`DocumentProcessorValidationTest`, `DocumentProcessorDuplicateTest`,
`DocumentApprovalServiceTest`).

### Fluxo completo por `curl`

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
#  -> 200; ao fim de 1-3 s:

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select status from documents where id='$DOC'"
#  -> EXTRACTED

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select rule_name, severity, passed from validation_results where document_id='$DOC' order by rule_name"
#  -> ARITHMETIC, DUPLICATE, ISSUE_DATE, MIN_CONFIDENCE, TAX_ID, VAT_RATE — todas WARNING/t
#     (APPROVAL_THRESHOLD e CATEGORY_SUGGESTION não escrevem linha: nada a assinalar)
```

**Executado nesta sessão** com o fluxo acima: documento `306ea9c5-...` ficou `EXTRACTED`,
com as 6 linhas exatas acima em `validation_results`.

## O que ficou por fazer

Nada em falta bloqueia a etapa 06. Fora de âmbito por decisão:

- **Endpoint REST de aprovação/rejeição e ecrã de revisão** — etapa 06 (API) e etapa 08
  (ecrã). `DocumentApprovalService` já está pronto a ligar-se a um controller.
- **Regras configuráveis por organização** — fixas em código, por decisão desta etapa.
- **Revalidação disparada por correção manual de um campo** — não há ainda quem corrija
  (etapa 06/08). `ValidationEngine.validate(...)` já está pronto para ser chamado de novo
  quando essa etapa chegar; não precisa de mudar.
- **Vídeo do terminal do marco desta etapa** ("no fim desta etapa o sistema funciona de
  ponta a ponta") — passo manual, não gravado nesta sessão.

## Armadilhas para a próxima sessão

1. **`ValidationEngine.deleteByDocumentId(...)` precisa de `results.flush()` logo a
   seguir, antes de gravar os resultados novos.** Sem isto, o Hibernate insere antes de
   apagar (a ordem de flush por omissão: inserts, depois deletes) e uma revalidação
   rebenta a constraint única em `(document_id, rule_name)`. Apanhado por
   `ValidationEngineTest.revalidatingReplacesThePreviousResultsInsteadOfAccumulating`. Se
   um dia reescreveres o motor para usar `saveAll` em lote ou uma query `@Modifying`,
   confirma que a ordem continua a ser apagar-e-confirmar antes de inserir.
2. **`PostgresContainerConfiguration` partilha o Postgres entre classes de teste com a
   mesma configuração `@Import`/`@ActiveProfiles`/propriedades** (é intencional, está
   documentado no próprio ficheiro). Isto era inofensivo antes desta etapa porque nada
   verificava duplicados; agora, qualquer teste `@SpringBootTest` que aprove um documento
   com o NIF+número da fixture "canónica" (`clean-invoice.json`, usada implicitamente por
   `DocumentProcessorTest`, `PipelineFlowTest`, `DlqRedriveTest`) deixa um duplicado falso
   para todos os outros que partilham esse contexto. A solução usada foi dar um
   `@TestPropertySource` com uma fixture diferente aos testes que aprovam documentos
   (`DocumentProcessorDuplicateTest`) — isso força um contexto (e um container Postgres)
   novo. Se acrescentares um teste que aprove um documento com a fixture por omissão,
   segue o mesmo padrão ou isola-o de outra forma.
3. **Fixtures do stub com data fixa apodrecem.** `clean-invoice.json`,
   `low-confidence.json` e `missing-field.json` têm `issueDate: "2026-08-20"` — dentro da
   janela de -24 meses/+7 dias por muito tempo a partir de agora, mas não para sempre. Se
   `PlausibleDateRuleTest` continuar verde mas o pipeline completo começar a dar
   `NEEDS_REVIEW` inesperado num "caminho feliz", é a primeira coisa a verificar.
   `ApprovalThresholdRule`/`CategorySuggestionRule` são as únicas regras `INFO` — não
   aparecem em `validation_results` quando não têm nada a dizer; não é bug se faltarem.
4. **`ExtractedField.readByMachine(...)` já regista a si próprio em
   `document.addExtractedField(...)`**; chamar `fields.save(...)` a seguir é redundante
   mas inofensivo (o cascade de `Document` trataria disto de qualquer forma). Manteve-se
   por clareza no `DocumentApprovalServiceTest`.
5. **`SupplierService` é `@Component` package-private em `com.docgrid.supplier`** — não
   dá para o referenciar por nome num `@Import` de um teste noutro pacote. Um
   `@SpringBootTest` completo (sem `@DataJpaTest`) apanha-o pelo scan normal da aplicação;
   é por isso que `DocumentApprovalServiceTest` usa `@SpringBootTest` +
   `CurrentUserProvider`/`DemoIdentityConfiguration` em vez de `@RepositoryTest` +
   `TestEntityManager` (que não dá acesso a beans `@Component` fora de repositórios).
6. **`DocumentWorker` só arranca no perfil `worker`** (`@Profile("worker")`), por isso um
   `@SpringBootTest` com perfil `test` nunca o levanta — não é preciso `LocalStackPipelineConfiguration`
   nos testes que não tocam em S3/SQS (`DocumentApprovalServiceTest` só importa
   `PostgresContainerConfiguration`).

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/validation/ValidationEngine.java` — a orquestração:
  corre as regras, grava, resume.
- `backend/src/main/java/com/docgrid/validation/ValidationRule.java` — o contrato que uma
  regra nova cumpre.
- `backend/src/main/java/com/docgrid/validation/TaxIdRule.java` — o dígito de controlo
  módulo 11.
- `backend/src/main/java/com/docgrid/validation/DuplicateRule.java` — os dois critérios
  numa regra.
- `backend/src/main/java/com/docgrid/document/DocumentProcessor.java` — `complete(...)` e
  `buildValidationContext(...)`: o ponto de integração.
- `backend/src/main/java/com/docgrid/document/DocumentApprovalService.java` — a aprovação
  sem endpoint.
- `backend/src/main/java/com/docgrid/supplier/Supplier.java` — o streak de categoria.
- `docs/adr/0010-motor-de-validacao-e-servico-de-aprovacao.md`

## Commits

```
99ec6cb docs(adr): motor de validação, duplicado e serviço de aprovação sem REST
4839b0f feat(document): serviço de aprovação que atualiza o histórico do fornecedor
bebe6ab feat(document): motor de validação integrado no processamento
82e4b29 test(validacao): cobre a orquestração do motor com regras deterministas
970238d feat(validacao): regras informativas de limite de aprovação e categoria
385bd2d feat(supplier): histórico do fornecedor e streak de aprovação por categoria
0205d60 feat(auth): porto para o limite de aprovação da organização
6e0c2f4 feat(validacao): regras de confiança mínima e de data plausível
f35a9ab feat(validacao): regra de duplicado por ficheiro e por NIF+número
4da4c5f feat(validacao): motor de validação com regras de aritmética, taxa de IVA e NIF
```
