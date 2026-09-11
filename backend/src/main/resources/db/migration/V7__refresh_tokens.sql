-- Refresh tokens da etapa 06. O access token é um JWT stateless, curto e nunca guardado;
-- o refresh token é opaco, longo, e só o seu hash SHA-256 chega à base de dados — perder
-- esta tabela nunca expõe um token utilizável. Ver docs/adr/0011.

create table refresh_tokens (
    id           uuid         primary key,
    user_id      uuid         not null references users (id),
    -- SHA-256 em hexadecimal: 64 caracteres, sempre.
    token_hash   varchar(64)  not null,
    expires_at   timestamptz  not null,
    -- Revogado por rotação (uso normal), por logout, ou porque um token já revogado voltou
    -- a ser apresentado (reutilização: revoga-se a sessão inteira, não só este token).
    revoked_at   timestamptz,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

create unique index uq_refresh_tokens_token_hash on refresh_tokens (token_hash);

-- Revogar toda a sessão de um utilizador (logout, deteção de reutilização) percorre por
-- utilizador; só interessam os que ainda não expiraram nem foram revogados.
create index idx_refresh_tokens_user on refresh_tokens (user_id)
    where revoked_at is null;
