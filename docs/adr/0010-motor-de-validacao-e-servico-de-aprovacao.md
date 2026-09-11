# 0010 — Motor de validação, duplicado por dois critérios, e aprovação sem REST

**Estado:** aceite · **Data:** 2026-09-11

## Contexto

A etapa 04 deixou a extração completa mas sem juízo: o `DocumentProcessor` transitava
sempre para `EXTRACTED`, sem verificar se o que foi lido faz sentido. A etapa 05 fecha
essa lacuna com um motor de regras de negócio. Três perguntas de desenho:

- Como estruturar o motor para que "acrescentar uma regra nova não toque em código
  existente" (critério de aceitação explícito) seja verdade e não só um objetivo?
- Duplicado é a mesma fatura (NIF+número) ou o mesmo ficheiro (hash)? A etapa deixou isto
  em aberto de propósito.
- "Atualização de `suppliers` a cada documento aprovado" está no âmbito da etapa, mas o
  endpoint de aprovação é trabalho da etapa 06 (API e autenticação). Como cumprir o
  primeiro sem antecipar o segundo?

## Decisão

**O motor é uma interface (`ValidationRule`) descoberta pelo Spring como `List<ValidationRule>`,
mais um `ValidationEngine` que as corre todas, persiste `validation_results` (revalidação
reescreve, via `deleteByDocumentId` seguido de `flush()` explícito — sem o `flush`, o
Hibernate insere antes de apagar e a constraint única em `(document_id, rule_name)`
rebenta) e devolve um `ValidationSummary` mínimo: `requiresReview` e uma `reason` de
texto. É a única coisa que atravessa a fronteira do pacote `validation` — os resultados
por regra continuam privados a esse pacote, testáveis lá dentro sem Spring. Cada regra
recebe um `ValidationContext`, um record com tudo pré-calculado (incluindo `today`,
passado explicitamente para a regra de data ser pura e testável sem depender do relógio)
— as regras não conhecem JPA nem outros pacotes. Adicionar uma regra é criar uma classe
`@Component`; nada mais muda.

**O duplicado é uma única regra (`DuplicateRule`) com dois critérios independentes**: hash
de ficheiro idêntico (contra qualquer documento não-`REJECTED` — reenviar o mesmo PDF
depois de uma rejeição não deve ficar preso num falso duplicado eterno) e NIF+número já
`APPROVED` (a leitura literal do `docs/01-PRODUCT.md`: "já existe... aprovado"). A deteção
em si — as duas consultas a `DocumentRepository` — é feita pelo `DocumentProcessor`, não
pela regra: o motor não deve conhecer o repositório de documentos. Mensagens diferentes
consoante o que foi encontrado, combinadas quando os dois batem.

**`DocumentApprovalService.approve(documentId, actor, category)` existe nesta etapa, sem
endpoint REST.** É a "API" a que o briefing se refere quando diz que o resultado "se
verifica por API ou base de dados" — uma API Java, chamada diretamente pelos testes; a
etapa 06 só terá de a ligar a um controller. Faz a transição para `APPROVED` e, se um
humano escolheu uma categoria, atualiza `suppliers` através de dois portos públicos novos
(`SupplierHistoryProvider`, leitura; `SupplierApprovalRecorder`, escrita — o mesmo padrão
de `CurrentUserProvider`). `Supplier.occurrenceCount` é o comprimento da sequência de
aprovações **consecutivas** na mesma categoria: muda de categoria reinicia a 1. Os dois
métodos primitivos que a etapa 02 tinha deixado (`recordOccurrence`, `rememberUsualCategory`,
sem nenhum chamador) tornam-se este único método coeso. O nome do fornecedor, necessário
para criar um `Supplier` novo, não vive em `documents` (ADR 0003) — vai-se buscar a
`extracted_fields`, com o próprio NIF como rede de segurança se faltar.

**O limite de aprovação e a sugestão de categoria são regras `INFO`, nunca bloqueiam** —
`ApprovalThresholdRule` e `CategorySuggestionRule` têm sempre `passed=true`; existem só
para deixar uma mensagem em `validation_results`. Isto reaproveita `ValidationSeverity`
sem alterá-lo: o enum já dizia, desde a etapa 02, que nenhuma severidade rejeita um
documento sozinha.

## Alternativas consideradas

- **Duplicado só por NIF+número, ou só por hash.** Os dois índices de `documents`
  (`idx_documents_org_supplier_invoice`, `idx_documents_org_file_hash`) já existiam desde
  a etapa 02 precisamente para os dois critérios — usar só um desperdiçava o desenho já
  feito.
- **Limiares (confiança 0.85, tolerâncias de IVA e data) configuráveis por organização.**
  Exigiria uma tabela nova e leitura por organização em cada validação, sem nenhum
  requisito atual que o justifique. Fixos em código.
- **Esperar pela etapa 06 para tocar em `suppliers`.** Contradiria o âmbito explícito da
  etapa 05. O compromisso foi construir o núcleo sem o endpoint, não adiar o núcleo.

## Consequências

**Torna fácil:** testar cada regra isoladamente sem Spring; adicionar uma regra sem tocar
nas outras (provado num commit que só acrescenta ficheiros); a etapa 06 ligar
`DocumentApprovalService` a um controller sem tocar na lógica de negócio.

**Torna difícil:** a "sugestão de categoria" só reflete a categoria das aprovações
*consecutivas* — um fornecedor que alterna duas categorias meio a meio nunca acumula
sequência nenhuma. Aceite: o schema só guarda uma categoria e uma contagem, e um histórico
completo por categoria é mais tabela do que o problema pede agora.
