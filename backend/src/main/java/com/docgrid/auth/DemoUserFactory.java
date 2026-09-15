package com.docgrid.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria os utilizadores da demonstração dentro de uma organização que já existe.
 *
 * <p>Vive em {@code com.docgrid.auth} e não em {@code com.docgrid.demo} por uma razão só:
 * {@code User}, {@code Organization} e os seus repositórios são internos ao pacote, como
 * manda o {@code docs/03-CONVENTIONS.md}, e o seed não os vê. Em vez de os abrir ao
 * mundo por causa de dados de demonstração, abre-se esta porta estreita — e só no perfil
 * {@code demo}.
 *
 * <p>O {@code AuthService.register(...)} cria a organização e o seu {@code ADMIN}, mas não
 * tem forma de criar os colegas: não há endpoint de gestão de utilizadores (fora de âmbito
 * do roteiro), e a demonstração precisa de um {@code FINANCE} que revê, de um
 * {@code MANAGER} que aprova acima do limite e de um {@code EMPLOYEE} que só submete.
 */
@Component
@Profile("demo")
public class DemoUserFactory {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    DemoUserFactory(OrganizationRepository organizations, UserRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** A password passa pelo mesmo {@link PasswordEncoder} do registo: o login funciona na mesma. */
    @Transactional
    public DemoUser create(UUID organizationId, String email, String fullName, UserRole role, String password) {
        User user = users.save(new User(organizationId, email, passwordEncoder.encode(password), fullName, role));
        return new DemoUser(user.getOrganizationId(), user.getId(), user.getRole());
    }

    /**
     * Quem já existe com este email. É por aqui que o seed descobre a identidade do
     * {@code ADMIN} que o registo criou, e é por aqui que sabe que já há dados semeados.
     */
    @Transactional(readOnly = true)
    public Optional<DemoUser> find(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(user -> new DemoUser(user.getOrganizationId(), user.getId(), user.getRole()));
    }

    /** Se a organização existe — a pergunta que o seed faz antes de escrever o que quer que seja. */
    @Transactional(readOnly = true)
    public boolean organizationExists(UUID organizationId) {
        return organizations.existsById(organizationId);
    }

    /**
     * Quem está a agir, no formato que o seed precisa: a organização, o utilizador e o
     * papel — exatamente o que um token JWT carregaria se isto passasse por HTTP.
     */
    public record DemoUser(UUID organizationId, UUID userId, UserRole role) {}
}
