-- O agregado documento: o ficheiro, o estado, os campos que foram lidos dele, o que a
-- validação disse desses campos, e o rasto de tudo o que lhe aconteceu.

create table documents (
    id                uuid           primary key,
    organization_id   uuid           not null references organizations (id),
    submitted_by      uuid           not null references users (id),

    -- Chave do objeto no S3. É conhecida no momento em que se emite a autorização de
    -- upload (etapa 02), portanto o registo existe antes de o ficheiro chegar — nada
    -- sobe sem rasto. Daí que tamanho e hash comecem nulos.
    storage_key       varchar(500)   not null,
    original_filename varchar(255)   not null,
    content_type      varchar(100)   not null,
    size_bytes        bigint,
    file_hash         varchar(64),

    status            varchar(20)    not null,

    -- Projeção dos campos extraídos, para procurar e agregar. A verdade, com a confiança
    -- de cada campo, está em extracted_fields; estas colunas são cópia de leitura escrita
    -- por um único ponto do código. Ver docs/adr/0003-desenho-de-extracted-fields.md.
    supplier_tax_id   varchar(20),
    invoice_number    varchar(60),
    issue_date        date,
    net_amount        numeric(12, 2),
    vat_amount        numeric(12, 2),
    -- Percentagem (23.00), não fração: é como aparece na fatura e como o contabilista
    -- a espera no CSV da etapa 09.
    vat_rate          numeric(5, 2),
    total_amount      numeric(12, 2),
    currency          varchar(3)     not null default 'EUR',

    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,

    constraint ck_documents_status check (status in (
        'UPLOADED', 'PROCESSING', 'EXTRACTED', 'NEEDS_REVIEW',
        'FAILED', 'APPROVED', 'REJECTED', 'EXPORTED')),
    constraint ck_documents_size_bytes check (size_bytes is null or size_bytes > 0)
);

-- Um objeto do S3 é um documento e só um. É esta constraint que torna possível a
-- idempotência do worker na etapa 03: a mensagem repetida não cria trabalho novo.
create unique index uq_documents_storage_key on documents (storage_key);

-- A fila de revisão e a de aprovação: filtrar por estado dentro da organização, do mais
-- recente para o mais antigo (etapas 06 e 08).
create index idx_documents_org_status_created
    on documents (organization_id, status, created_at desc);

-- Regra do duplicado (etapa 05): a mesma fatura do mesmo fornecedor.
create index idx_documents_org_supplier_invoice
    on documents (organization_id, supplier_tax_id, invoice_number)
    where supplier_tax_id is not null and invoice_number is not null;

-- Duplicado binário: o mesmo ficheiro submetido duas vezes (etapa 05).
create index idx_documents_org_file_hash
    on documents (organization_id, file_hash)
    where file_hash is not null;

create table extracted_fields (
    id          uuid          primary key,
    document_id uuid          not null references documents (id) on delete cascade,
    field_name  varchar(40)   not null,
    value_text  varchar(500),
    -- 0.000 a 1.000. O que o motor de extração achou deste campo.
    confidence  numeric(4, 3),
    source      varchar(10)   not null,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,

    constraint ck_extracted_fields_name check (field_name in (
        'SUPPLIER_NAME', 'SUPPLIER_TAX_ID', 'INVOICE_NUMBER', 'ISSUE_DATE',
        'NET_AMOUNT', 'VAT_AMOUNT', 'VAT_RATE', 'TOTAL_AMOUNT', 'CURRENCY', 'CATEGORY')),
    constraint ck_extracted_fields_source check (source in ('AI', 'HUMAN')),
    constraint ck_extracted_fields_confidence_range
        check (confidence is null or (confidence >= 0 and confidence <= 1)),
    -- A regra 6 do AGENTS.md em forma de constraint: um campo lido pela máquina não pode
    -- perder o seu grau de confiança pelo caminho. Um campo escrito por uma pessoa não
    -- tem confiança para guardar — a origem HUMAN já diz tudo.
    constraint ck_extracted_fields_confidence_required check (
        (source = 'AI' and confidence is not null)
        or (source = 'HUMAN' and confidence is null))
);

-- Um valor corrente por campo. O valor anterior de uma correção humana fica em
-- document_events, não aqui: ver docs/adr/0003-desenho-de-extracted-fields.md.
create unique index uq_extracted_fields_document_field
    on extracted_fields (document_id, field_name);

create table validation_results (
    id          uuid        primary key,
    document_id uuid        not null references documents (id) on delete cascade,
    rule_name   varchar(50) not null,
    severity    varchar(10) not null,
    passed      boolean     not null,
    -- Em português: esta mensagem vai aparecer a quem revê o documento.
    message     varchar(500),
    created_at  timestamptz not null,
    updated_at  timestamptz not null,

    constraint ck_validation_results_severity check (severity in ('INFO', 'WARNING', 'ERROR'))
);

-- O resultado corrente de cada regra. Revalidar reescreve, não acumula.
create unique index uq_validation_results_document_rule
    on validation_results (document_id, rule_name);

create table document_events (
    id            bigint      generated always as identity primary key,
    -- Sem `on delete cascade`, ao contrário das tabelas acima, e de propósito: a chave
    -- estrangeira torna impossível apagar um documento sem apagar primeiro o seu
    -- histórico. O histórico não se apaga, logo o documento também não.
    document_id   uuid        not null references documents (id),
    event_type    varchar(30) not null,
    from_status   varchar(20),
    to_status     varchar(20),
    actor_type    varchar(10) not null,
    actor_user_id uuid        references users (id),
    reason        varchar(500),
    payload       jsonb,
    occurred_at   timestamptz not null,

    constraint ck_document_events_type
        check (event_type in ('CREATED', 'STATUS_CHANGED', 'FIELD_CORRECTED')),
    constraint ck_document_events_actor_type check (actor_type in ('USER', 'SYSTEM')),
    -- Uma pessoa identifica-se; o sistema não tem utilizador. Sem isto, um evento podia
    -- dizer-se humano e não dizer de quem.
    constraint ck_document_events_actor check (
        (actor_type = 'USER' and actor_user_id is not null)
        or (actor_type = 'SYSTEM' and actor_user_id is null))
);

-- O histórico de um documento, por ordem (etapa 08).
create index idx_document_events_document_occurred
    on document_events (document_id, occurred_at);
