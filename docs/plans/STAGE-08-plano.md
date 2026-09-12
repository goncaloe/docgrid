# Plano — Etapa 08: Ecrã de revisão

**Data:** 2026-09-12 · **Planeado com:** Claude Opus 5 · **Estado:** aprovado

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.

## Contexto

A etapa 07 deixou a aplicação React a funcionar (login, lista, fila de revisão, upload), mas a
fila de revisão é só visibilidade: não há forma de ver um documento, corrigi-lo, aprová-lo ou
rejeitá-lo. Toda a API para isso existe desde a etapa 06 e nunca foi consumida.

Esta é a tela que o projeto mostra em entrevistas (`stages/STAGE-08-revisao-ui.md`,
`docs/06-DEMO-CHECKLIST.md`): o documento original à esquerda, os campos à direita, a
incerteza do modelo visível, e a região da imagem a acender quando se foca um campo. É onde
se vê que a saída de um modelo é probabilística e que o desenho reflete isso.

## O que se vai construir

1. **Três campos em falta na API** — sem eles a etapa não é possível: a geometria de cada campo,
   o tipo de ficheiro do documento, e o id do documento duplicado.
2. **Rota `/review/:id`** — painel dividido, com contador de posição na fila.
3. **Pré-visualização com overlay** — PDF por `react-pdf`, imagens por `<img>`, polígonos em SVG.
4. **Formulário de campos** — confiança em percentagem, destaque amarelo abaixo de 0,85,
   mensagens de validação junto ao campo em causa, correção gravada ao sair do campo.
5. **Fluxo de fila** — aprovar (Ctrl+Enter) e avançar para o seguinte, rejeitar com motivo (Esc),
   cartão de duplicado com link para o original e confirmação explícita antes de aprovar.

## Pré-requisitos verificados

- **Etapa 07**: `frontend/` com Vite + React + Mantine, `apiFetch` com renovação de sessão
  (`frontend/src/api/client.ts`), `RequireRole` (`frontend/src/auth/RequireRole.tsx`), Vitest + MSW.
- **Etapa 04**: geometria guardada — `extracted_fields.page` e `bounding_box` jsonb
  (`backend/src/main/resources/db/migration/V6__extracted_fields_geometry.sql`), polígonos
  normalizados 0–1 (`com.docgrid.extraction.FieldGeometry`), e fixtures locais já com polígonos
  (`backend/src/main/resources/extraction/fixtures/low-confidence.json`).
- **Etapa 06**: `GET /api/documents/{id}`, `PATCH /api/documents/{id}/fields/{fieldName}`,
  `POST /api/documents/{id}/approve`, `POST /api/documents/{id}/reject`,
  `GET /api/documents/{id}/file-url` — todos em `com.docgrid.document.DocumentController` e
  `DocumentUploadController`, restritos a `FINANCE`/`MANAGER`/`ADMIN`.
- **CORS do bucket local** já permite `GET`/`HEAD` de `http://localhost:5173`
  (`docker/localstack/init/ready.d/docgrid-resources.sh`) — o browser lê o ficheiro diretamente.

### O que falta, e é esta etapa que tapa

| Lacuna | Onde | Consequência sem isto |
|---|---|---|
| `page` e `boundingBox` não saem na API | `document/dto/ExtractedFieldResponse.java` | não há destaque na imagem |
| `contentType` não sai na API | `document/dto/DocumentDetailResponse.java` | não se sabe se renderizar PDF ou imagem |
| id do duplicado só existe dentro do texto da mensagem | `validation/DuplicateRule.java` | não há link para o original |

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **`react-pdf` (pdf.js) no browser** | Imagem gerada no servidor (PDFBox + endpoint + cache) | o overlay precisa das coordenadas da página renderizada; o servidor exigiria render, armazenamento e custo AWS na etapa 11, por trabalho de backend numa etapa de frontend |
| idem | `<iframe>`/`<embed>` nativos | o URL pré-assinado traz `Content-Disposition: attachment` (`S3StorageService.createDownloadUrl`), que força download; e o visualizador nativo não deixa sobrepor nada por cima |
| **Correção gravada ao sair do campo**, um `PATCH` por campo alterado | rascunho local, gravar tudo ao aprovar | `FieldCorrectionService` já revalida e audita por campo: o revisor vê a mensagem de erro desaparecer no momento, nada se perde se fechar o browser, e o Esc não fica ambíguo |
| **Duplicado: reutilizar `DocumentValidationContextFactory` no `DocumentResponseMapper`** | coluna `related_document_id` em `validation_results` (migração + `RuleOutcome` + engine + 8 testes de regras) | mesma consulta que a validação usa, dois ficheiros Java, sem migração — e fica atualizado se o original for entretanto rejeitado |
| idem | extrair o UUID da mensagem por regex no frontend | o link partia em silêncio ao reescrever a frase da `DuplicateRule` |
| **Mapa regra → campos afetados no frontend** | o backend declarar os campos em `ValidationRule` | é conhecimento de apresentação; alterar a interface mexia em 8 regras e nos seus testes |
| **Limiar de 0,85 duplicado no frontend** como constante documentada | endpoint de configuração | precedente da etapa 07 (a validação de upload no cliente espelha `docgrid.upload`) |
| **Uma rota `/review/:id`**, adaptativa: editável em `EXTRACTED`/`NEEDS_REVIEW`, leitura nos outros estados | rota de detalhe separada da de revisão | um ecrã só; o estado do documento decide o que se pode fazer, tal como o backend já decide |
| **Sem `@mantine/form` nem `@mantine/modals`** | instalá-los, como a ADR-0001 previa | com gravação ao sair do campo, sincronizar um form state com o server state só acrescenta uma fonte de bugs; o `Modal` do core chega para rejeitar e confirmar |
| **Confirmação do duplicado no cliente** | bloquear a aprovação no backend | o backend não tem esse conceito, e o produto diz que o sistema não substitui o humano — sinaliza e deixa decidir |

ADR a escrever: `docs/adr/0012-render-de-pdf-no-browser.md` — `react-pdf`/`pdfjs-dist` não estavam
na tabela de módulos por etapa da ADR-0001, e a alternativa (render no servidor) é defensável.
Regista também o abandono de `@mantine/form` e `@mantine/modals` face ao que a ADR-0001 previa.

## Por decidir — parar aqui

- **Nada bloqueante.** Se durante a implementação o `pdfjs-dist` não arrancar com a versão do
  Vite deste projeto (ver Riscos), **para e pergunta** antes de trocar de biblioteca de render.

## Passos de implementação

Por ordem. Cada passo é uma unidade de commit.

### 1. API: geometria, tipo de ficheiro e duplicado no detalhe

- `backend/src/main/java/com/docgrid/document/dto/PolygonPointResponse.java` (criar) —
  `public record PolygonPointResponse(double x, double y) {}`, normalizado 0–1.
- `backend/src/main/java/com/docgrid/document/dto/ExtractedFieldResponse.java` (alterar) —
  acrescenta `Integer page` e `List<PolygonPointResponse> boundingBox`; ambos `null`/vazio num
  campo de origem `HUMAN`, tal como a `confidence` (ver o comentário já lá).
- `backend/src/main/java/com/docgrid/document/dto/DocumentDetailResponse.java` (alterar) —
  acrescenta `String contentType` e `UUID duplicateOfDocumentId`.
- `backend/src/main/java/com/docgrid/document/DocumentValidationContextFactory.java` (alterar) —
  torna `findDuplicateInvoiceDocumentId` e `findDuplicateFileDocumentId` package-private e
  acrescenta `UUID duplicateOf(Document)`: devolve o duplicado de fatura se existir, senão o de
  ficheiro (a regra de negócio do produto é "mesmo NIF + número já aprovado").
- `backend/src/main/java/com/docgrid/document/DocumentResponseMapper.java` (alterar) — injeta
  `DocumentValidationContextFactory` e `ObjectMapper`; preenche os campos novos. O polígono está
  guardado como string JSON `[[x,y],...]` (`ExtractedField.getBoundingBox()`, package-private,
  mesmo pacote): desserializa com `ObjectMapper` para `double[][]` e converte, devolvendo `null`
  quando a coluna é nula.
- Testes: `backend/src/test/java/com/docgrid/document/DocumentControllerTest.java` (alterar) —
  o detalhe traz `contentType`, `fields[].page`, `fields[].boundingBox` com os 4 pontos do
  polígono, e `duplicateOfDocumentId` preenchido quando há duplicado e `null` quando não há.
- Commit: `feat(api): geometria, tipo de ficheiro e duplicado no detalhe do documento`

### 2. Frontend: tipos e chamadas novas

- `frontend/src/api/types.ts` (alterar) — `PolygonPoint { x: number; y: number }`;
  `ExtractedFieldResponse` ganha `page: number | null` e `boundingBox: PolygonPoint[] | null`;
  `DocumentDetailResponse` ganha `contentType: string` e `duplicateOfDocumentId: string | null`;
  `FieldCorrectionRequest`, `ApproveRequest`, `RejectRequest`.
- `frontend/src/api/documents.ts` (alterar) — `correctField(id, fieldName, value)` (`PATCH`),
  `approveDocument(id, category?)` (`POST`), `rejectDocument(id, reason)` (`POST`, devolve 204).
- Commit: `feat(frontend): tipos e chamadas de correção, aprovação e rejeição`

### 3. Render de PDF: dependência, worker e ADR

- `cd frontend && npm install react-pdf` (traz `pdfjs-dist`). Fixa as versões no `package.json`.
- `frontend/src/features/review/pdfWorker.ts` (criar) — configura
  `pdfjs.GlobalWorkerOptions.workerSrc` com
  `new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString()`. Importado uma vez
  por `DocumentPreview.tsx`.
- `docs/adr/0012-render-de-pdf-no-browser.md` (criar) — ver "Decisões tomadas".
- Commit: `chore(frontend): render de PDF no browser com react-pdf`

### 4. Pré-visualização com overlay de bounding boxes

- `frontend/src/features/review/useDocumentFile.ts` (criar) — `useQuery` sobre `getFileUrl(id)`,
  `staleTime` abaixo do TTL do URL pré-assinado, `retry: 1`.
- `frontend/src/features/review/BoundingBoxOverlay.tsx` (criar) — `<svg viewBox="0 0 1 1"
  preserveAspectRatio="none">` em `position: absolute; inset: 0`, um `<polygon>` por campo com
  `points` das coordenadas normalizadas. Sem conversão para pixels: o `viewBox` faz a escala.
  Cada polígono leva `data-field={fieldName}`; o campo ativo recebe classe própria (traço cheio,
  preenchimento translúcido), os restantes ficam esbatidos.
- `frontend/src/features/review/DocumentPreview.tsx` (criar) — `contentType` decide:
  `application/pdf` → `<Document file={url}><Page pageNumber={page} width={width}
  renderTextLayer={false} renderAnnotationLayer={false} /></Document>`;
  `image/*` → `<img src={url}>`. Wrapper com `position: relative` e o overlay por cima. Estado de
  carregamento e de erro (URL expirado → botão para pedir outro). Multi-página: mostra a página do
  campo ativo; sem campo ativo, a página 1.
- `frontend/src/features/review/DocumentPreview.module.css` (criar).
- Testes: `frontend/src/features/review/BoundingBoxOverlay.test.tsx` — polígono do campo ativo
  distinto dos outros, e coordenadas escritas tal como vieram (0–1).
- Commit: `feat(frontend): pré-visualização do documento com as caixas dos campos`

### 5. Formulário de campos com confiança e validação

- `frontend/src/features/review/reviewFields.ts` (criar) — `FIELD_ORDER` (SUPPLIER_NAME,
  SUPPLIER_TAX_ID, INVOICE_NUMBER, ISSUE_DATE, NET_AMOUNT, VAT_AMOUNT, VAT_RATE, TOTAL_AMOUNT,
  CURRENCY, CATEGORY), rótulos em português, e `CONFIDENCE_THRESHOLD = 0.85` com comentário a
  apontar para `MinConfidenceRule.THRESHOLD`.
- `frontend/src/features/review/validationMessages.ts` (criar) — mapa `ruleName → campos`:
  `ARITHMETIC` → net/vat/total, `VAT_RATE` → vatRate/vat/net, `TAX_ID` → supplierTaxId,
  `ISSUE_DATE` → issueDate, `APPROVAL_THRESHOLD` → totalAmount, `CATEGORY_SUGGESTION` → category;
  `MIN_CONFIDENCE` e `DUPLICATE` não se ligam a campo (o primeiro já se vê na percentagem de cada
  campo, o segundo tem cartão próprio). Comentário a apontar para `com.docgrid.validation`.
- `frontend/src/features/review/ReviewField.tsx` (criar) — um `TextInput` com rascunho local:
  `onFocus` avisa quem o contém (destaque na imagem), `onBlur` grava se o valor mudou, `Escape`
  dentro do campo repõe o valor do servidor sem gravar. Mostra a confiança em percentagem
  (`Badge` + `Tooltip`), fundo amarelo com `.fieldUncertain` abaixo do limiar, "corrigido por si"
  quando `source === "HUMAN"`, e as mensagens de validação que lhe pertencem.
- `frontend/src/features/review/ReviewField.module.css` (criar) — `.fieldUncertain` com as
  variáveis de tema do Mantine (ADR-0001).
- `frontend/src/features/review/ReviewFieldsForm.tsx` (criar) — desenha os campos por
  `FIELD_ORDER`, distribui as mensagens, e chama a mutação de correção. Depois de cada correção,
  invalida `["document", id]` e `["review-queue"]` (o estado pode mudar). A **categoria não é
  gravada por `PATCH`**: vai no corpo do `approve` (`ApproveRequest.category`) — assinala-a como
  "Categoria (sugerida)".
- Testes: `frontend/src/features/review/ReviewFieldsForm.test.tsx` — campo abaixo do limiar sai
  destacado e com a percentagem; sair do campo sem alterar não faz pedido; alterar e sair faz
  `PATCH` uma vez; a mensagem do `ARITHMETIC` aparece junto ao total e não junto ao NIF.
- Commit: `feat(frontend): campos extraídos com confiança visível e correção in-loco`

### 6. Página de revisão, fila e atalhos

- `frontend/src/features/review/useReviewQueueNavigation.ts` (criar) — `listReviewQueue(0, 50)`,
  devolve `{ position, total, nextId }` para o id atual; se o documento não estiver nessa página,
  devolve tudo a `null` e o contador não aparece.
- `frontend/src/features/review/DuplicateCard.tsx` (criar) — `Alert` com a mensagem da regra
  `DUPLICATE` e link para `/review/{duplicateOfDocumentId}`.
- `frontend/src/features/review/RejectModal.tsx` (criar) — `Modal` do core, motivo obrigatório
  (`@NotBlank` no backend), confirma com Ctrl+Enter.
- `frontend/src/features/review/ReviewPage.tsx` (criar) — `useParams`, `getDocument`, painel
  dividido (`Grid`: pré-visualização à esquerda, formulário à direita; numa coluna abaixo de
  `md`). Cabeçalho com `DocumentStatusBadge`, nome do ficheiro, contador "3 de 12" e os botões
  Aprovar / Rejeitar com `Kbd`. Aprovar invalida `["review-queue"]` e navega para `nextId`; sem
  seguinte, volta a `/review-queue` com notificação. Se houver duplicado, aprovar abre primeiro um
  `Modal` de confirmação explícita. Documento não editável (`APPROVED`, `REJECTED`, `EXPORTED`,
  `FAILED`, `UPLOADED`, `PROCESSING`) → campos em leitura e botões escondidos.
  Atalhos com `useHotkeys` de `@mantine/hooks`: `mod+Enter` aprova, `Escape` abre a rejeição —
  **com `tagsToIgnore` vazio**, senão não disparam com o foco dentro de um campo.
- `frontend/src/features/review/approvalAuthority.ts` (criar) — o `GlobalExceptionHandler`
  devolve "Sem permissão para esta operação" sem detalhe, por isso o ecrã antecipa: se o papel é
  `FINANCE` e existe resultado `APPROVAL_THRESHOLD`, ou algum campo de montante tem
  `source === "HUMAN"`, avisa que a aprovação exige gestor (regras em `DocumentApprovalService`).
- `frontend/src/App.tsx` (alterar) — rota `/review/:id` dentro de `RequireAuth` + `AppShellLayout`
  + `RequireRole allowed={REVIEW_ROLES}`.
- `frontend/src/features/documents/DocumentsTable.tsx` (alterar) — prop opcional
  `onRowClick?: (id: string) => void`; a linha fica clicável e alcançável por teclado
  (`tabIndex`, `Enter`). `ReviewQueuePage.tsx` e `DocumentsListPage.tsx` passam a navegar para
  `/review/{id}`.
- Testes: `frontend/src/features/review/ReviewPage.test.tsx` — contador de posição; Ctrl+Enter
  aprova e navega para o seguinte; Esc abre a rejeição e o motivo é obrigatório; duplicado exige
  confirmação antes de aprovar; focar um campo marca o polígono correspondente como ativo;
  documento aprovado não mostra botões. `useReviewQueueNavigation.test.ts` — posição e seguinte.
- `frontend/src/test/handlers.ts` (alterar) — detalhe com geometria, `PATCH`, `approve`, `reject`,
  `file-url`. Mock de `react-pdf` (`vi.mock`) e fixtures com `image/jpeg` para o caminho `<img>`.
- Commit: `feat(frontend): ecrã de revisão com atalhos e avanço na fila`

### 7. Verificação visual e GIF

- Iterar visualmente com o stack de pé (ver Critérios): espaçamento, contraste do amarelo em
  fundo claro, largura do painel, foco visível. O briefing diz para não aceitar o primeiro
  resultado.
- Gravar o GIF do ecrã (arrastar, corrigir um campo, aprovar) para `docs/assets/revisao.gif` e
  referenciá-lo no `README.md`.
- Commit: `docs: GIF do ecrã de revisão`

## Critérios de aceitação

```bash
# backend
npm test

# frontend
cd frontend && npm install && npx tsc -b --noEmit && npx eslint . && npx vitest run
```

Resultado esperado: backend verde; frontend sem erros de tipos, 0 erros de ESLint, todos os
testes a passar.

Manual, com `npm run up` na raiz e `cd frontend && npm run dev`, com a fixture
`docgrid.extraction.stub-fixture=extraction/fixtures/low-confidence.json`:

1. `/review-queue` → clicar numa linha abre `/review/{id}` com o documento à esquerda.
2. Campos com confiança abaixo de 0,85 estão a amarelo, com a percentagem visível.
3. Focar `NET_AMOUNT` acende o polígono correspondente na imagem.
4. Corrigir o IVA e sair do campo: a mensagem "a base mais o IVA não bate certo com o total"
   desaparece sem recarregar a página.
5. Ctrl+Enter aprova e salta para o documento seguinte; o contador desce.
6. Esc abre a rejeição; sem motivo não deixa confirmar.
7. Com a fixture `duplicate-check`, submeter a mesma fatura duas vezes: o segundo documento
   mostra o cartão com link para o original e aprovar exige confirmação.
8. Todo o percurso 1–6 feito só com teclado.
9. Aprovar um documento demora menos de 5 segundos com o rato.

## Fora de âmbito

- Dashboard e exportação → etapa 09.
- Ecrã da DLQ → etapa 10.
- `npm run seed` com 60 documentos de demonstração → etapa 12.
- Verificação em ecrã de telemóvel, herdada por confirmar da etapa 07 → confirma-se aqui de
  passagem, nas DevTools, já que o painel dividido tem de colapsar numa coluna.

## Riscos conhecidos

- **Worker do pdf.js com Vite.** Se o `workerSrc` não resolver, a pré-visualização fica em branco
  sem erro visível. Confirma na consola do browser antes de avançar. Se a versão do `pdfjs-dist`
  não bater com a que o `react-pdf` espera, fixa-a explicitamente no `package.json`.
- **pdf.js não corre em jsdom.** Nos testes, `vi.mock("react-pdf")` e fixtures de imagem para o
  caminho `<img>`. Não tentes renderizar um PDF a sério em Vitest.
- **`useHotkeys` ignora `INPUT`/`TEXTAREA` por omissão** — passa `tagsToIgnore` vazio, ou os
  atalhos não funcionam com o foco num campo, que é onde ele está sempre.
- **URL pré-assinado expira** (`docgrid.upload.url-ttl`). Numa sessão longa de revisão a imagem
  deixa de carregar: trata o erro e volta a pedir `file-url`.
- **`Select`/`Combobox` do Mantine** — se acrescentares um, passa-lhe
  `comboboxProps={{ keepMounted: false }}`, senão a suite de testes desse ficheiro passa de
  milissegundos a segundos (armadilha 1 do handoff da etapa 07).
- **Correções de montante por um `FINANCE`** bloqueiam a aprovação no backend com um 403 sem
  detalhe. O aviso antecipado do passo 6 evita o beco sem saída; não o cortes.
