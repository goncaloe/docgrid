# 0009 — `AnalyzeExpense`, geometria em `jsonb` e a fronteira dos erros de extração

**Estado:** aceite · **Data:** 2026-09-10

## Contexto

A etapa 03 extraía campos com constantes hard-coded, sem confiança honesta nem sítio
onde o campo tinha sido lido. A etapa 04 exige o contrário: um motor de verdade (o
Textract), cada campo com confiança e bounding box até à interface, e uma fronteira clara
entre o que é permanente (a foto é má) e o que é transitório (o serviço não respondeu).

Três perguntas de desenho, todas com mais de uma resposta defensável:

- Que API do Textract usar: `AnalyzeExpense` ou `AnalyzeDocument` com queries?
- Onde guardar a geometria dos campos?
- O que fazer quando o serviço devolve dois candidatos para o mesmo campo — e quando
  falha?

## Decisão

**`AnalyzeExpense`.** É o modelo de despesas do Textract: devolve campos tipados
(`VENDOR_NAME`, `SUBTOTAL`, `TAX`, `TOTAL`, …) com `ValueDetection` que já traz confiança
e geometria normalizada. O `AnalyzeDocument` com queries devolve blocos de texto
genéricos — associar texto a campo teria de ser heurística nossa, exatamente o trabalho
que o serviço pago existe para fazer. A API é síncrona (resposta imediata), sem jobs
assíncronos.

**A geometria vive numa coluna `bounding_box jsonb` e `page int` em `extracted_fields`** —
polígono com pontos normalizados 0–1 (o próprio Textract devolve assim) como array de
pares `[[x,y],...]`. O frontend sobrepõe o polígono à imagem ou ao PDF sem conhecer as
dimensões da página. A constraint `ck_extracted_fields_bbox_source` é a simetria de
`ck_extracted_fields_confidence_required`: um campo `AI` sabe sempre onde foi lido, um
`HUMAN` já não o sabe (a correção apaga a geometria — quem corrigiu escreveu um valor
novo, sem coordenadas). Descartadas: colunas float8 (só retângulo; o Textract dá
polígono) e tabela própria de geometrias (uma junção por campo sem ganho).

**Dois candidatos para o mesmo campo: fica o de maior confiança; em empate, o primeiro da
resposta** (ordem de iteração estável), com log a registar o descartado. Não há fusão de
valores — combinar dois números numa fatura é inventar dados.

**O `supplier_name` entra; `currency` e `category` não.** O nome do fornecedor vive em
`extracted_fields` com a sua confiança (a projeção de `documents` não ganha coluna —
ADR 0003 mantém-se). Currency tem default `'EUR'` e o Textract não a devolve de forma
fiável; categoria é sugestão por histórico de fornecedor, trabalho de outra etapa.

**A taxa de IVA só entra se o Textract a devolver como campo próprio** (`TAX_PCT`).
Derivar `vat/net` é inferência — e é exatamente o que a validação da etapa 05 existe para
verificar. O extractor não faz o trabalho do validador.

**Erros, em duas categorias e nenhuma mais:**

- *Documento ilegível* (`UnsupportedDocumentException`, `BadDocumentException`,
  `InvalidParameterException`) → `UnreadableDocumentException`, erro **permanente**: o
  `DocumentProcessor` faz `fail(...)` logo à primeira, sem esperar pelas três tentativas
  do SQS. Repetir a chamada não torna a foto menos tremida.
- *Serviço indisponível* (throttling, 5xx, rede, timeout) → `RuntimeException` genérica →
  o caminho existente da entrega SQS trata do resto (3 tentativas → DLQ).

O timeout da chamada é propriedade (`apiCallTimeout`, 60 s por omissão): uma análise de
fatura demora segundos, e quem estoura o timeout sai como transitório.

**Perfis:** `StubExtractor` em `local` e `test` (dirigido por fixtures em
`docgrid.extraction.stub-fixture`); `TextractExtractor` e o cliente em `aws`. Trocar de
perfil troca de implementação, sem tocar em código de negócio. O cliente do Textract nem
sequer é construído fora de `aws` — as análises pagam-se por documento.

## Consequências

**Torna fácil:** sobrepor caixas ao documento na interface (a geometria já está na linha
do campo); simular casos maus sem uma chamada paga (uma fixture nova, não um documento
novo); distinguir na base de dados um campo lido de um campo corrigido; trocar o motor de
extração sem tocar no worker.

**Torna difícil:** manter as fixtures aritmeticamente consistentes com as regras que a
etapa 05 vai impor — um stub que viola as regras confunde os testes de pipeline. E o
`bounding_box jsonb` é opaco à JPA (String com `@JdbcTypeCode`): nenhum índice, nenhuma
query espacial — se um dia for preciso procurar por geometria, a decisão reabre.

**Desvio ao briefing:** as fixtures do stub ficam em `src/main/resources/extraction/fixtures/`
e não em `src/test/resources` — o stub também corre no perfil `local`, e o classpath de
main não vê test resources. As respostas gravadas do Textract ficam em
`src/test/resources/textract/`, como o briefing pedia.
