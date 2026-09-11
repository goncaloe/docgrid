package com.docgrid.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * Uma sessão de refresh. O token em si nunca se guarda — só o seu hash SHA-256 — para que
 * perder esta tabela nunca exponha um token utilizável.
 *
 * <p>Revogado por rotação (o uso normal: pedir um refresh consome este e emite outro), por
 * logout, ou porque um token já revogado voltou a ser apresentado — nesse caso revoga-se a
 * sessão inteira do utilizador, não só este token. Ver
 * {@code docs/adr/0011-autenticacao-jwt-e-isolamento-por-organizacao.md}.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}

    RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isActive(Instant now) {
        return !isRevoked() && expiresAt.isAfter(now);
    }

    UUID getUserId() {
        return userId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
