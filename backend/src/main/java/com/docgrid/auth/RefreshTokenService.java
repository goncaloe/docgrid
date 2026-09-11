package com.docgrid.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite, roda e revoga refresh tokens. O token que sai daqui em texto claro nunca se guarda
 * — só o seu hash SHA-256, na tabela {@code refresh_tokens}.
 *
 * <p>Rodar é o uso normal de um refresh: o token apresentado é revogado e um novo é emitido
 * na mesma chamada. Um token já revogado que volte a ser apresentado é sinal de que alguém
 * o copiou — não se sabe quem, atacante ou o próprio dono com um separador de rede lento —
 * e a resposta prudente é revogar toda a sessão desse utilizador, não só aceitar ou recusar
 * este pedido.
 */
@Service
class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final long ttlMillis;

    RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties) {
        this.tokens = tokens;
        this.ttlMillis = properties.refreshTokenTtl().toMillis();
    }

    @Transactional
    String issue(UUID userId) {
        String plaintext = randomToken();
        tokens.save(new RefreshToken(userId, hash(plaintext), Instant.now().plusMillis(ttlMillis)));
        return plaintext;
    }

    @Transactional
    Rotated rotate(String plaintext) {
        RefreshToken token = tokens.findByTokenHash(hash(plaintext)).orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked()) {
            revokeAll(token.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (!token.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        token.revoke();
        return new Rotated(token.getUserId(), issue(token.getUserId()));
    }

    @Transactional
    void revoke(String plaintext) {
        tokens.findByTokenHash(hash(plaintext)).ifPresent(RefreshToken::revoke);
    }

    private void revokeAll(UUID userId) {
        tokens.findByUserIdAndRevokedAtIsNull(userId).forEach(RefreshToken::revoke);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não existe nesta JVM", e);
        }
    }

    record Rotated(UUID userId, String refreshToken) {}
}
