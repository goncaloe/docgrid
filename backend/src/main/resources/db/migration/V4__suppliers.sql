-- Histórico por fornecedor, que sustenta a sugestão de categoria da etapa 05: se este NIF
-- já apareceu cinco ou mais vezes sempre na mesma categoria, sugere-se essa, com o
-- histórico como justificação.
--
-- Não há chave estrangeira de documents para aqui, de propósito. Um documento guarda o NIF
-- que foi lido dele, mesmo quando esse fornecedor ainda não é conhecido — e o primeiro
-- documento de um fornecedor novo tem de poder existir antes de o fornecedor existir.

create table suppliers (
    id               uuid         primary key,
    organization_id  uuid         not null references organizations (id),
    tax_id           varchar(20)  not null,
    name             varchar(200) not null,
    -- Sugerida, nunca imposta. Nula enquanto não houver histórico que a sustente.
    usual_category   varchar(50),
    occurrence_count integer      not null default 0,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,

    constraint ck_suppliers_occurrence_count check (occurrence_count >= 0)
);

-- O mesmo NIF é o mesmo fornecedor dentro de uma organização, e organizações diferentes
-- mantêm o seu próprio histórico do mesmo fornecedor.
create unique index uq_suppliers_organization_tax_id on suppliers (organization_id, tax_id);
