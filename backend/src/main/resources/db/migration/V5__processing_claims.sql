-- A tabela da idempotência do worker (etapa 03). O SQS garante entrega at-least-once:
-- a mesma mensagem pode chegar duas vezes, e duas entregas simultâneas podem até chegar
-- a caminho de workers diferentes. É esta tabela que decide, atomicamente, quem processa.
-- Ver docs/adr/0007-idempotencia-do-worker.md.

-- Uma linha por objeto já reclamado pelo pipeline. A chave natural é a chave do S3: é
-- ela que vem na mensagem, e um objeto é um documento e só um (uq_documents_storage_key).
create table processing_claims (
    storage_key  varchar(500) primary key,
    document_id  uuid        not null references documents (id),
    claimed_at   timestamptz not null,
    -- Nula enquanto o trabalho está em curso; o preenchimento acontece na mesma
    -- transação que passa o documento a EXTRACTED. É esta coluna que distingue
    -- "duplicado verdadeiro, nada a fazer" de "tentativa interrompida, retomar".
    completed_at timestamptz,

    constraint ck_processing_claims_completed_after_claimed
        check (completed_at is null or completed_at >= claimed_at)
);

create index idx_processing_claims_document on processing_claims (document_id);
