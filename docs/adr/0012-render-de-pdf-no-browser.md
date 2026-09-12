# 0012 — Render de PDF no browser, sem `@mantine/form` nem `@mantine/modals`

**Estado:** aceite · **Data:** 2026-09-12

## Contexto

O ecrã de revisão (etapa 08) sobrepõe as caixas dos campos extraídos ao documento
original. Um documento pode ser uma imagem (foto de telemóvel) ou um PDF; nenhuma das
duas bibliotecas de UI escolhidas na ADR-0001 — Mantine e TanStack Table — trata disto,
e a tabela de módulos por etapa dessa ADR não previa uma biblioteca de PDF.

## Decisão 1 — `react-pdf` (pdf.js), render no browser

O overlay precisa das coordenadas da página tal como é desenhada: um polígono normalizado
0–1 sobrepõe-se por cima de qualquer render dessa página, na mesma escala. `react-pdf`
expõe `<Page>` com essa dimensão conhecida; um `<img>` cobre o caso de imagem com o mesmo
mecanismo.

### Alternativas consideradas

- **Render no servidor** (PDFBox, já na stack pela extração, mais um endpoint e cache de
  imagens): resolveria o mesmo problema, mas exige armazenamento e política de cache que
  só fazem sentido decidir na etapa 11 (infraestrutura AWS), por um ganho nenhum nesta
  etapa — seria trabalho de backend só para poupar uma dependência de frontend.
- **`<iframe>`/`<embed>` nativos do browser**: o URL pré-assinado do S3
  (`S3StorageService.createDownloadUrl`) traz `Content-Disposition: attachment`, que força
  o download em vez de mostrar o PDF; e o visualizador nativo do browser não permite
  sobrepor nada por cima do que desenha.

### Consequências

`pdfjs-dist` (dependência transitiva de `react-pdf`) exige um *worker* configurado à parte
(`features/review/pdfWorker.ts`) — sem ele, a pré-visualização fica em branco sem erro
visível. As versões de `react-pdf` e `pdfjs-dist` ficam fixas no `package.json` (sem `^`):
a versão mais recente do `react-pdf` (11.x) já exige React 19, que este projeto não usa
(ADR-0001, React 18) — `react-pdf@10.5.0` é a última versão compatível.

pdf.js não corre em jsdom: os testes que envolvem `DocumentPreview` fazem `vi.mock("react-pdf")`
e usam fixtures de imagem (`image/jpeg`) para o caminho `<img>`, que é o que se pode
verificar em Vitest.

## Decisão 2 — Sem `@mantine/form` nem `@mantine/modals`

A ADR-0001 previa instalá-los quando o frontend precisasse de formulários e diálogos. Esta
etapa precisa de ambos, mas com uma forma que os torna dispensáveis.

### Alternativas consideradas

- **`@mantine/form`**, como a ADR-0001 previa: geriria o estado do formulário de campos, mas
  cada campo grava com um `PATCH` independente ao sair do campo (não há um "submeter tudo"),
  por isso sincronizar um form state completo com o server state só acrescentaria uma fonte
  de bugs sem resolver problema nenhum que o estado local por campo (`ReviewField.tsx`) já
  não resolva.
- **`@mantine/modals`**: o `Modal` do pacote `@mantine/core`, já instalado, chega para os
  dois casos desta etapa (motivo de rejeição, confirmação de duplicado) — não há diálogos
  encadeados nem imperativos a justificar o gestor de modais dedicado.

### Consequências

Se uma etapa futura precisar de um formulário com submissão única (não campo a campo) ou de
diálogos encadeados, é aí que `@mantine/form`/`@mantine/modals` entram — não há decisão
tomada aqui contra eles em geral, só contra instalá-los sem um uso concreto.
