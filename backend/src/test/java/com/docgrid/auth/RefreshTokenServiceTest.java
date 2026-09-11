package com.docgrid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.docgrid.support.RepositoryTest;

/** Emissão, rotação e revogação de refresh tokens contra o Postgres real. */
@RepositoryTest
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private TestEntityManager entityManager;

    private RefreshTokenService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(
                tokens, new JwtProperties("segredo", Duration.ofMinutes(15), Duration.ofDays(30)));
        UUID organizationId = AuthFixtures.organization(entityManager);
        userId = AuthFixtures.user(entityManager, organizationId);
    }

    @Test
    void issuedTokenRotatesToANewOneAndRevokesTheOld() {
        String issued = service.issue(userId);

        RefreshTokenService.Rotated rotated = service.rotate(issued);

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.refreshToken()).isNotEqualTo(issued);
        assertThatThrownBy(() -> service.rotate(issued))
                .as("o token original já foi consumido pela primeira rotação")
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reusingARevokedTokenRevokesTheWholeSession() {
        String first = service.issue(userId);
        RefreshTokenService.Rotated rotated = service.rotate(first);

        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThatThrownBy(() -> service.rotate(rotated.refreshToken()))
                .as("a reutilização do token revogado devia ter revogado também o seu sucessor")
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesTheToken() {
        String issued = service.issue(userId);

        service.revoke(issued);

        assertThatThrownBy(() -> service.rotate(issued)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void anUnknownTokenIsRefused() {
        assertThatThrownBy(() -> service.rotate("um-token-que-nunca-existiu"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
