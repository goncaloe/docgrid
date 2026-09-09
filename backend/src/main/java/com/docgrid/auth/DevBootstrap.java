package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semeia uma organização e um utilizador de demonstração no arranque, no perfil
 * {@code local}, para o fluxo de upload ter a quem atribuir os documentos antes de a
 * etapa 06 trazer autenticação. Idempotente: reconhece o utilizador pelo email.
 *
 * <p>Serve também de {@link CurrentUserProvider} nesse perfil — guarda os ids depois de
 * semear, e assim um pedido não vai à base de dados só para saber quem é o utilizador
 * fixo. A etapa 06 apaga esta classe inteira: o provider passa a ler o token do pedido.
 */
@Component
@Profile("local")
@Order(0)
class DevBootstrap implements ApplicationRunner, CurrentUserProvider {

    static final String DEMO_EMAIL = "dev@docgrid.local";

    private static final Logger log = LoggerFactory.getLogger(DevBootstrap.class);

    private final OrganizationRepository organizations;
    private final UserRepository users;

    private volatile UUID organizationId;
    private volatile UUID userId;

    DevBootstrap(OrganizationRepository organizations, UserRepository users) {
        this.organizations = organizations;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User demo = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(this::seed);
        this.organizationId = demo.getOrganizationId();
        this.userId = demo.getId();
        log.info("Demonstração local: organização {} · utilizador {} <{}>", organizationId, userId, DEMO_EMAIL);
    }

    private User seed() {
        Organization organization = organizations.save(
                new Organization("DocGrid Demonstração, Lda.", "501442889", new BigDecimal("1000.00")));
        return users.save(new User(
                organization.getId(),
                DEMO_EMAIL,
                "$2a$10$hash-de-mentira",
                "Programador de Demonstração",
                UserRole.FINANCE));
    }

    @Override
    public UUID currentOrganizationId() {
        return requireSeeded(organizationId);
    }

    @Override
    public UUID currentUserId() {
        return requireSeeded(userId);
    }

    private static UUID requireSeeded(UUID id) {
        if (id == null) {
            throw new IllegalStateException("DevBootstrap ainda não semeou o utilizador de demonstração");
        }
        return id;
    }
}
