package com.docgrid.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Emissão e validação do access token, sem levantar o Spring — é uma classe pura. */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "segredo-de-teste-com-pelo-menos-32-bytes-garantidos", Duration.ofMinutes(15), Duration.ofDays(30)));

    @Test
    void aFreshlyIssuedTokenCarriesTheClaimsItWasIssuedWith() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String token = jwtService
                .issueAccessToken(userId, organizationId, UserRole.MANAGER)
                .token();

        Optional<JwtService.AccessTokenClaims> claims = jwtService.parse(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().organizationId()).isEqualTo(organizationId);
        assertThat(claims.get().role()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    void refusesAMalformedToken() {
        assertThat(jwtService.parse("isto-nao-e-um-jwt")).isEmpty();
    }

    @Test
    void refusesATokenSignedWithAnotherSecret() {
        JwtService otherIssuer = new JwtService(new JwtProperties(
                "outro-segredo-completamente-diferente-32-bytes!!", Duration.ofMinutes(15), Duration.ofDays(30)));
        String token = otherIssuer
                .issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN)
                .token();

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void refusesAnExpiredToken() {
        JwtService shortLived = new JwtService(new JwtProperties(
                "segredo-de-teste-com-pelo-menos-32-bytes-garantidos", Duration.ofMillis(1), Duration.ofDays(30)));
        String token = shortLived
                .issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.EMPLOYEE)
                .token();

        await(() -> assertThat(shortLived.parse(token)).isEmpty());
    }

    private static void await(Runnable assertion) {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertion.run();
    }
}
