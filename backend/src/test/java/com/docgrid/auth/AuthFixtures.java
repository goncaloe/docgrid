package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Organizações e utilizadores para os testes de outros pacotes.
 *
 * <p>Vive em {@code com.docgrid.auth} porque {@code Organization} e {@code User} são
 * package-private e assim continuam: quem chama recebe apenas identificadores, que é tudo
 * o que precisa para satisfazer as chaves estrangeiras.
 *
 * <p>Duas famílias de sobrecargas: {@link TestEntityManager} para testes {@code @DataJpaTest}
 * (via {@code @RepositoryTest}), {@link EntityManager} simples para testes
 * {@code @SpringBootTest} — que não têm {@code TestEntityManager} disponível.
 */
public final class AuthFixtures {

    private AuthFixtures() {}

    public static UUID organization(TestEntityManager entityManager) {
        return persistOrganization(entityManager::persist);
    }

    public static UUID organization(EntityManager entityManager) {
        return persistOrganization(entityManager::persist);
    }

    public static UUID user(TestEntityManager entityManager, UUID organizationId) {
        return user(entityManager, organizationId, "ana.ribeiro@padaria.pt");
    }

    public static UUID user(TestEntityManager entityManager, UUID organizationId, String email) {
        return persistUser(entityManager::persist, organizationId, email, UserRole.FINANCE);
    }

    public static UUID user(EntityManager entityManager, UUID organizationId, String email, UserRole role) {
        return persistUser(entityManager::persist, organizationId, email, role);
    }

    private static UUID persistOrganization(java.util.function.Consumer<Object> persist) {
        Organization organization = new Organization("Padaria do Bairro, Lda.", "501442889", new BigDecimal("500.00"));
        persist.accept(organization);
        return organization.getId();
    }

    private static UUID persistUser(
            java.util.function.Consumer<Object> persist, UUID organizationId, String email, UserRole role) {
        User user = new User(organizationId, email, "$2a$10$hash-de-mentira", "Ana Ribeiro", role);
        persist.accept(user);
        return user.getId();
    }
}
