-- Índice composto para as agregações do dashboard: totais por período, filtro por
-- organização e estado, agrupamento por mês e por categoria.
--
-- A cobertura de (organization_id, status, issue_date) permite ao Postgres fazer uma
-- só varrimento no índice (Index-Only Scan) quando as colunas pedidas — nomeadamente
-- net_amount, vat_amount, total_amount — também estão no índice, mas não estão, por
-- enquanto. Ainda assim, reduz a fatura de I/O: o Postgres filtra organização, estado e
-- data no índice e só depois vai à tabela buscar os valores.
create index idx_documents_org_status_issue_date
    on documents (organization_id, status, issue_date);