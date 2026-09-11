package com.docgrid.auth;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Emite access tokens verdadeiros para testes de outros pacotes que exercem a API a sério
 * (MockMvc). {@code JwtService} é package-private — este é o único ponto público de onde um
 * teste de {@code com.docgrid.document}, por exemplo, consegue um token válido sem passar
 * pelo fluxo HTTP de login.
 */
@Component
public class JwtTestSupport {

    private final JwtService jwtService;

    JwtTestSupport(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public String accessToken(UUID userId, UUID organizationId, UserRole role) {
        return jwtService.issueAccessToken(userId, organizationId, role).token();
    }
}
