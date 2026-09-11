package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dá aos testes de fluxo completo uma organização e um utilizador a quem atribuir os
 * documentos, e um {@link CurrentUserProvider} que os devolve — sem passar por HTTP nem
 * por um token real. Os testes que exercem a API a sério (autorização, papéis) mintam um
 * token verdadeiro em vez de usar esta configuração — ver {@code JwtTestSupport}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DemoIdentityConfiguration {

    public static final String DEMO_EMAIL = "dev@docgrid.local";

    @Bean
    @Primary
    FixedCurrentUserProvider demoIdentity(OrganizationRepository organizations, UserRepository users) {
        return new FixedCurrentUserProvider(organizations, users);
    }

    static class FixedCurrentUserProvider implements ApplicationRunner, CurrentUserProvider {

        private final OrganizationRepository organizations;
        private final UserRepository users;

        private volatile UUID organizationId;
        private volatile UUID userId;

        FixedCurrentUserProvider(OrganizationRepository organizations, UserRepository users) {
            this.organizations = organizations;
            this.users = users;
        }

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
            User demo = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(this::seed);
            this.organizationId = demo.getOrganizationId();
            this.userId = demo.getId();
        }

        private User seed() {
            Organization organization = organizations.save(
                    new Organization("DocGrid Demonstração, Lda.", "501442889", new BigDecimal("1000.00")));
            return users.save(new User(
                    organization.getId(),
                    DEMO_EMAIL,
                    "$2a$10$hash-de-mentira",
                    "Utilizador de Teste",
                    UserRole.FINANCE));
        }

        @Override
        public UUID currentOrganizationId() {
            return require(organizationId);
        }

        @Override
        public UUID currentUserId() {
            return require(userId);
        }

        @Override
        public UserRole currentRole() {
            return UserRole.FINANCE;
        }

        private static UUID require(UUID id) {
            if (id == null) {
                throw new IllegalStateException("Identidade de teste ainda não semeada");
            }
            return id;
        }
    }
}
