# 0013 — Read model do dashboard e da exportação

**Estado:** aceite · **Data:** 2026-09-14

## Contexto

O dashboard (etapa 09) agrega centenas de documentos em totais, séries mensais, categorias
e fornecedores, e a exportação tem de selecionar os aprovados de cada período. No outro
extremo, `com.docgrid.document` é o pacote grande do sistema (60+ ficheiros) e trata do
ciclo de vida: o serviço de aprovação grava a projeção e os eventos, o enum de estados
impõe as transições, e as entidades não saem do pacote.

A pergunta é onde vivem as agregações: dentro de `document`, ou fora.

## Decisão

As agregações vivem em SQL (com `JdbcClient`, sem entidades) num pacote novo
`com.docgrid.dashboard`, declarado **read model**: só lê, nunca escreve. O pacote
`com.docgrid.export` lê as suas linhas do CSV também por SQL, mas a única escrita que faz
em `documents` — a transição para `EXPORTED` — passa pela porta pública
`document.DocumentExportService`, que chama `Document.transitionTo` (a garantia da etapa 01:
toda a transição passa por `DocumentStatus.canTransitionTo` e deixa evento em
`document_events`).

Regra que fica escrita, e que qualquer pacote futuro tem de respeitar:

> **Ler por SQL está permitido a um read model; escrever fá-lo só o pacote dono da tabela,
> e sempre pela sua porta pública.**

### Alternativas consideradas

- **Carregar os documentos em Java e agregar em memória**: somar 5000 linhas em memória para
  mostrar 12 pontos é trabalho e memória a mais; `group by` com o índice adequado é o que o
  Postgres faz melhor. E abrir as entidades de `document` a outro pacote quebraria o
  encapsulamento que a etapa 00 estabeleceu.
- **Meter as agregações dentro de `document`**: o pacote já é grande e o seu tema é o
  ciclo de vida; o read model não partilha nada com ele para além das tabelas. Cada
  agregação nova teria de arranjar sítio dentro da fronteira do pacote em vez de ter
  um sítio evidente onde ir.
- **Expor uma segunda porta para mudar o estado a partir de `export`** (p. ex. um
  `DocumentRepository` público ou um UPDATE direto): a transição `APPROVED → EXPORTED`
  é terminal no produto; deixar um pacote vizinho escrevê-la à margem do enum apagava a
  garantia que o `DocumentStatusTest` (etapa 01) comprova. A porta pública
  `DocumentExportService.markExported` é o único caminho, e recebe o `exportId` para gravar
  também a projeção `documents.export_id`.

### Consequências

- `DashboardService` é `@Transactional(readOnly = true)` e `ExportService` só abre
  transação para as escritas da porta; quem lê estas classes não tem de se preocupar com
  escrita.
- As consultas levam um índice próprio (`idx_documents_org_status_issue_date`) porque o que
  já existia é por `created_at` e não serve para agrupar por `issue_date`.
- A taxa de automação e o tempo médio de revisão vivem aqui, calculados a partir dos eventos
  de `document_events` — são agregações, não estado do documento.
- Quando uma etapa futura pedir outra vista agregada (p. ex. por organização), o sítio é
  um read model novo ou mais uma consulta neste pacote, nunca um endpoint que devolva
  entidades.
