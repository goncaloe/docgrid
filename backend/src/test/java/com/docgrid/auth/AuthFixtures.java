package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Organizações e utilizadores para os testes de outros pacotes.
 *
 * <p>Vive em {@code com.docgrid.auth} porque {@code Organization} e {@code User} são
 * package-private e assim continuam: quem chama recebe apenas identificadores, que é tudo
 * o que precisa para satisfazer as chaves estrangeiras.
 */
public final class AuthFixtures {

    private AuthFixtures() {}

    public static UUID organization(TestEntityManager entityManager) {
        Organization organization = new Organization("Padaria do Bairro, Lda.", "501442889", new BigDecimal("500.00"));
        entityManager.persist(organization);
        return organization.getId();
    }

    public static UUID user(TestEntityManager entityManager, UUID organizationId) {
        return user(entityManager, organizationId, "ana.ribeiro@padaria.pt");
    }

    public static UUID user(TestEntityManager entityManager, UUID organizationId, String email) {
        User user = new User(organizationId, email, "$2a$10$hash-de-mentira", "Ana Ribeiro", UserRole.FINANCE);
        entityManager.persist(user);
        return user.getId();
    }
}
