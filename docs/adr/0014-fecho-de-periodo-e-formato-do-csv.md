# 0014 — Fecho de período e formato do CSV

**Estado:** aceite · **Data:** 2026-09-14

## Contexto

A exportação mensal (etapa 09) é o ponto onde o DocGrid toca o processo real da empresa: um
contabilista externo recebe um CSV com as faturas do mês e mete-o no seu software. Duas
coisas não estavam decididas: quando pertence um documento a um mês, e como se comportam os
que chegam tarde.

## Decisão 1 — O período é o da fatura, não o do trabalho

Um documento pertence à exportação do mês da sua `issue_date` (a data da fatura), não da
`created_at` (quando alguém a fotografou). O contabilista trabalha com as datas das faturas.
Os indicadores operacionais do dashboard (taxa de automação, tempo de revisão) esses sim
usam `created_at`, porque são sobre o trabalho do mês.

Um documento aprovado quando o seu mês já se fechou não fica invisível: entra na exportação
seguinte como **lançamento extemporâneo**. O CSV leva a data real da fatura, e o
contabilista vê-a. É o que faz a contabilidade.

### Alternativas consideradas

- **Usar `created_at` para tudo**: misturaria faturas de um mês com trabalho de outro, e o
  contabilista não poderia casar o CSV com as suas anotações.
- **Só `issue_date` dentro do mês fechado**: um documento aprovado tarde, de um mês já
  fechado, desapareceria da contabilidade. O princípio de "não inventar dados" (sinaliza,
  não adivinha) não admite perdê-lo.

## Decisão 2 — Não se reabre um período fechado

`EXPORTED` é terminal: no enum (`DocumentStatusTest`) e no produto. Reabrir obrigaria a
inventar a transição `EXPORTED → APPROVED`, a contradizer `docs/01-PRODUCT.md` e a permitir
alterar dados que o contabilista já recebeu. Se algo está mal, corrige-se com um documento
novo de correção, como já se faz com `APPROVED`.

O fecho é **idempotente por (organização, ano, mês)**: o índice único `uq_exports_org_period`
na tabela `exports` faz com que o segundo `POST` do mesmo período devolva 200 com a
exportação que já existe, sem gerar nada nem mudar estados. Em condição de corrida ganha a
primeira inserção, e a segunda relê e devolve a que já está.

### Alternativas consideradas

- **Reabrir com auditoria**: obrigaria a uma transição nova (`EXPORTED → APPROVED`) e à
  possibilidade de contradizer o ficheiro que já está no S3. O costume no domínio é um
  documento de correção, não tocar no do mês fechado.
- **Deixar criar duas exportações do mesmo mês**: contradiria o critério de aceitação
  da etapa, que exige não duplicar.

## Decisão 3 — O CSV abre no Excel português com um duplo clique

O ficheiro gera-se no servidor, grava-se no S3 (`storage.put`) e descarrega-se por URL
pré-assinado (o mesmo padrão que os documentos). Formato:

- **UTF-8 com BOM** (`EF BB BF`): sem ele, o Excel abre os acentos trocados.
- **Separador `;`**: com uma vírgula, o Excel português parte as casas decimais em colunas.
- **Vírgula decimal, sem separador de milhares**: `1234,56`, nunca `1.234,56`.
- **Fim de linha `\r\n`**, datas `dd-MM-yyyy`, e quoting RFC 4180 (aspas só quando o valor
  contém `;`, `"`, `\r` ou `\n`; aspas internas duplicadas).

Cabeçalho fixo de onze colunas, por esta ordem:
`Data;Nº da fatura;Fornecedor;NIF;Base tributável;Taxa IVA;IVA;Total;Categoria;Moeda;Ficheiro`.

### Alternativas consideradas

- **Devolver o CSV no corpo da resposta**: perder-se-ia o padrão de URL pré-assinado já
  testado, e o ficheiro fechado deve ficar imutável e voltar a poder descarregar-se. Além
  disso, um link de descarga não leva cabeçalho `Authorization` nos navegadores.
- **`;` vs `,` como separador**: com `;`, o Excel abre sem assistente de importação; com a
  vírgula, um campo com décimas parte a coluna.

## Consequências

- A ordem de escrita é: gerar e gravar o CSV no S3, depois a transação que grava o
  `Export` e passa os documentos a `EXPORTED`. Um ficheiro órfão no S3 (transação que
  falha depois do upload) não tem consequência funcional; o contrário — um registo que
  aponta para um ficheiro inexistente — tem.
- Um `APPROVED` sem `issue_date` não pode ir para nenhum período: fica fora do ficheiro, mas
  a resposta conta-os (`documentsWithoutDate`) para que ninguém pense que se perderam.
- `POST /api/exports` devolve 201 à primeira vez e 200 quando o período já estava fechado;
  a interface usa o status para avisar, não para fingir que exportou agora.
