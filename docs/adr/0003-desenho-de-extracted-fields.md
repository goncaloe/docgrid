# 0003 — Campos extraídos em tabela própria, com projeção em `documents`

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

Cada campo lido de uma fatura traz um valor **e** um grau de confiança, e mais tarde traz
também a origem: lido pela máquina ou escrito por uma pessoa. A regra 6 do `AGENTS.md` é
explícita — essa informação tem de chegar até à interface, e nunca ser descartada pelo
caminho.

A forma óbvia de guardar uma fatura é uma coluna por campo em `documents`. Só que uma
coluna guarda um valor, não um triplo (valor, confiança, origem). Guardar os três exigiria
trinta colunas para dez campos, e cada campo novo seria uma migração com três colunas.

A alternativa é uma tabela com uma linha por campo. Mas isso empurra o custo para o outro
lado: sobre uma linha por campo, procurar a fatura duplicada de um fornecedor passa a ser
um duplo `EXISTS` sobre a mesma tabela, e somar o IVA de um mês passa a exigir um pivot.
São exatamente as duas consultas que as etapas 05 e 09 precisam de fazer bem.

## Decisão

**As duas coisas, com papéis separados e sem ambiguidade sobre qual manda.**

`extracted_fields` é a fonte de verdade: uma linha por campo, com `value_text`,
`confidence`, `source` e `unique (document_id, field_name)`. É onde a confiança vive e é o
que a interface de revisão lê para saber onde mandar o utilizador olhar.

`documents` leva um punhado de colunas de projeção — `supplier_tax_id`, `invoice_number`,
`issue_date`, `net_amount`, `vat_amount`, `vat_rate`, `total_amount`, `currency` — que são
cópia de leitura dos mesmos valores, sem confiança nem origem. Existem para procurar e
agregar, e sustentam o índice
`(organization_id, supplier_tax_id, invoice_number)` que a regra do duplicado precisa.

O que impede a divergência das duas é haver um só caminho de escrita:
`Document.projectInvoiceFields(InvoiceFields)`. Nenhum outro método toca nessas colunas.

**O histórico das correções fica em `document_events`,** e não em versões de
`extracted_fields`. Uma correção humana escreve um evento `FIELD_CORRECTED` com o valor
antigo e o novo no `payload jsonb`.

Duas constraints sustentam o modelo na base de dados, e não apenas no Java:

- `ck_extracted_fields_confidence_required` — um campo com origem `AI` tem de trazer
  confiança, e um com origem `HUMAN` não pode trazer nenhuma. É a regra 6 em SQL.
- `uq_extracted_fields_document_field` — um valor corrente por campo.

## Alternativas consideradas

**Só colunas em `documents`.** O modelo mais simples de ler e de consultar, e o mais
rápido de escrever. Descartado porque não tem onde guardar a confiança, e sem confiança
por campo desaparece o que distingue este projeto de um formulário: a interface deixa de
poder dizer *onde* olhar e passa a dizer apenas "confira tudo". Seria abdicar da regra 6
para poupar uma junção.

**Só `extracted_fields`, sem projeção.** Uma só verdade, zero duplicação, e o modelo mais
honesto dos três. Descartado pelo custo nas consultas que mais interessam: a regra do
duplicado da etapa 05 e as agregações do dashboard da etapa 09 passariam a viver sobre
pivots, e o índice "por NIF e número" que o modelo precisa não teria onde assentar — as
duas colunas nunca estariam na mesma linha.

**Versões em `extracted_fields`, com `superseded_at`.** Guardaria o histórico ao lado do
campo, que é onde intuitivamente se procura. Descartado porque obrigaria todas as leituras
a filtrar pela versão corrente, e uma consulta futura que se esqueça desse filtro não
falha — devolve valores antigos misturados com atuais, silenciosamente. Uma tabela de
histórico à parte não tem essa armadilha.

**`jsonb` em `documents` com os campos todos.** Sem migrações a cada campo novo.
Descartado por perder tipagem e índices onde eles fazem mais falta, precisamente nas
colunas por onde se procura.

## Consequências

**Torna fácil:** mostrar confiança por campo em toda a interface; acrescentar um campo
extraído sem migração de esquema, apenas um valor no enum e na constraint; procurar
duplicados e agregar totais com SQL simples e índices normais; auditar quem alterou o quê,
com o valor anterior.

**Torna difícil:** manter as duas representações coerentes. É duplicação, e duplicação
diverge quando há mais do que um sítio a escrever. A mitigação é estrutural — um só método
escreve a projeção — mas depende de disciplina de quem lá mexer, e vale a pena verificá-la
em revisão de código. Ler a fatura completa custa uma consulta a mais que um modelo de
colunas, o que a esta escala não se nota.

**Deixa em aberto:** se as duas representações divergirem em produção, a reconciliação faz-se
a partir de `extracted_fields`, que é a verdade. Reprojetar é reler as linhas do documento e
voltar a chamar `projectInvoiceFields` — não há informação a perder no sentido inverso.
