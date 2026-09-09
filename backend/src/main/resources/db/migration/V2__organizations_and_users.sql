-- Organizações e utilizadores.
--
-- É aqui que assenta o isolamento por organização: toda a consulta das etapas
-- seguintes filtra por organization_id, sem exceção.
--
-- Estados e papéis são varchar com CHECK, não tipos enum nativos do Postgres.
-- Ver docs/adr/0004-estados-do-documento-e-auditoria.md.

create table organizations (
    id                 uuid           primary key,
    name               varchar(200)   not null,
    tax_id             varchar(20),
    approval_threshold numeric(12, 2) not null,
    created_at         timestamptz    not null,
    updated_at         timestamptz    not null,

    constraint ck_organizations_approval_threshold check (approval_threshold >= 0)
);

comment on column organizations.approval_threshold is
    'Total em EUR acima do qual a despesa exige aprovação de um gestor.';

create table users (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    email           varchar(320) not null,
    password_hash   varchar(100) not null,
    full_name       varchar(200) not null,
    role            varchar(20)  not null,
    -- Quem sai da empresa desativa-se; apagar levaria consigo o rasto de quem
    -- submeteu o quê. Ver docs/adr/0004.
    deactivated_at  timestamptz,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,

    constraint ck_users_role check (role in ('EMPLOYEE', 'FINANCE', 'MANAGER', 'ADMIN'))
);

-- O email identifica a pessoa em todo o sistema e não apenas dentro da organização:
-- na autenticação (etapa 06) ainda não se sabe a que organização ela pertence.
create unique index uq_users_email on users (lower(email));

create index idx_users_organization on users (organization_id);
