-- Tabela de exportações mensais fechadas. Uma exportação é imutável depois de criada:
-- os documentos saem do ciclo de revisão e a sua projeção de negócio fica registada
-- no CSV que se arquiva no S3.
--
-- O par (organization_id, period_year, period_month) é único: fechado o mês, não há
-- segunda exportação para o mesmo período.
create table exports (
    id              uuid          primary key,
    organization_id uuid          not null references organizations(id),
    period_year     integer       not null,
    period_month    integer       not null,
    storage_key     varchar(500)  not null,
    document_count  integer       not null,
    net_total       numeric(14,2) not null,
    vat_total       numeric(14,2) not null,
    total           numeric(14,2) not null,
    created_by      uuid          not null references users(id),
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    constraint ck_exports_period_month   check (period_month between 1 and 12),
    constraint ck_exports_document_count check (document_count >= 0)
);

create unique index uq_exports_org_period
    on exports (organization_id, period_year, period_month);

-- Ligação dos documentos à exportação em que foram fechados. Nula até à exportação.
-- Índice parcial: só cobre as linhas que interessam (as exportadas), sem ruído.
alter table documents
    add column export_id uuid references exports(id);

create index idx_documents_export
    on documents (export_id)
    where export_id is not null;