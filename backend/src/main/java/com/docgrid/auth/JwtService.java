package com.docgrid.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Emite e valida o access token: um JWT assinado (HS256), stateless, de vida curta. O
 * refresh token não é um JWT — é opaco, ver {@link RefreshTokenService}.
 */
@Service
class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlMillis;

    JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMillis = properties.accessTokenTtl().toMillis();
    }

    IssuedAccessToken issueAccessToken(UUID userId, UUID organizationId, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(accessTokenTtlMillis);
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    /** {@code Optional.empty()} para qualquer token ilegível, mal assinado ou expirado. */
    Optional<AccessTokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("org", String.class)),
                    UserRole.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record AccessTokenClaims(UUID userId, UUID organizationId, UserRole role) {}

    record IssuedAccessToken(String token, Instant expiresAt) {}
}
