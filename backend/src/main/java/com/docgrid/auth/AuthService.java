package com.docgrid.auth;

import java.math.BigDecimal;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.dto.TokenResponse;

/**
 * Registo, login, refresh e logout. O acesso à base de dados fica todo aqui; o controller
 * só traduz pedidos HTTP.
 */
@Service
public class AuthService {

    /**
     * Limite de aprovação por omissão para uma organização recém-registada. Sem interface
     * para o escolher no registo ainda (fora de âmbito); um administrador pode vir a mudá-lo
     * mais tarde.
     */
    private static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("1000.00");

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    AuthService(
            OrganizationRepository organizations,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokens) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    /**
     * Cria a organização e o seu primeiro utilizador, com papel {@code ADMIN}, e devolve-lhe
     * sessão iniciada.
     *
     * @throws EmailAlreadyRegisteredException se o email já pertencer a alguém
     */
    @Transactional
    public TokenResponse register(
            String organizationName,
            String organizationTaxId,
            String adminEmail,
            String adminPassword,
            String adminFullName) {
        if (users.findByEmailIgnoreCase(adminEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException(adminEmail);
        }
        Organization organization =
                organizations.save(new Organization(organizationName, organizationTaxId, DEFAULT_APPROVAL_THRESHOLD));
        User admin = users.save(new User(
                organization.getId(),
                adminEmail,
                passwordEncoder.encode(adminPassword),
                adminFullName,
                UserRole.ADMIN));
        return issueTokens(admin);
    }

    /** @throws InvalidCredentialsException se o email não existir, estiver inativo, ou a password não bater certo */
    @Transactional
    public TokenResponse login(String email, String password) {
        User user =
                users.findByEmailIgnoreCase(email).filter(User::isActive).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /** @throws InvalidRefreshTokenException se o token não existir, tiver expirado ou já tiver sido usado */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);
        User user = users.findById(rotated.userId()).orElseThrow(InvalidRefreshTokenException::new);
        JwtService.IssuedAccessToken accessToken =
                jwtService.issueAccessToken(user.getId(), user.getOrganizationId(), user.getRole());
        return new TokenResponse(accessToken.token(), accessToken.expiresAt(), rotated.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private TokenResponse issueTokens(User user) {
        JwtService.IssuedAccessToken accessToken =
                jwtService.issueAccessToken(user.getId(), user.getOrganizationId(), user.getRole());
        String refreshToken = refreshTokens.issue(user.getId());
        return new TokenResponse(accessToken.token(), accessToken.expiresAt(), refreshToken);
    }
}
