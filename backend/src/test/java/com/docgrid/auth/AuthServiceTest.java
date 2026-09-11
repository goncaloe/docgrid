package com.docgrid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.docgrid.auth.dto.TokenResponse;
import com.docgrid.support.PostgresContainerConfiguration;

/** Registo, login, refresh e logout — contra o Postgres real, sem HTTP à mistura. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class AuthServiceTest {

    @Autowired
    private AuthService auth;

    @Test
    void registerCreatesTheOrganizationAndLogsTheAdminIn() {
        TokenResponse tokens = auth.register(
                "Padaria do Bairro, Lda.", "501442889", uniqueEmail(), "uma-password-forte", "Ana Ribeiro");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void registeringTheSameEmailTwiceIsRefused() {
        String email = uniqueEmail();
        auth.register("Padaria do Bairro, Lda.", "501442889", email, "uma-password-forte", "Ana Ribeiro");

        assertThatThrownBy(
                        () -> auth.register("Outra Empresa, Lda.", "501442890", email, "outra-password", "Outro Nome"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginWithTheRightPasswordSucceeds() {
        String email = uniqueEmail();
        auth.register("Padaria do Bairro, Lda.", "501442889", email, "uma-password-forte", "Ana Ribeiro");

        TokenResponse tokens = auth.login(email, "uma-password-forte");

        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void loginWithTheWrongPasswordIsRefused() {
        String email = uniqueEmail();
        auth.register("Padaria do Bairro, Lda.", "501442889", email, "uma-password-forte", "Ana Ribeiro");

        assertThatThrownBy(() -> auth.login(email, "password-errada")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithAnUnknownEmailIsRefused() {
        assertThatThrownBy(() -> auth.login(uniqueEmail(), "qualquer-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesTheSessionAndLogoutEndsIt() {
        TokenResponse first = auth.register(
                "Padaria do Bairro, Lda.", "501442889", uniqueEmail(), "uma-password-forte", "Ana Ribeiro");

        TokenResponse refreshed = auth.refresh(first.refreshToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(first.refreshToken());

        auth.logout(refreshed.refreshToken());
        assertThatThrownBy(() -> auth.refresh(refreshed.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static String uniqueEmail() {
        return "ana." + java.util.UUID.randomUUID() + "@padaria.pt";
    }
}
