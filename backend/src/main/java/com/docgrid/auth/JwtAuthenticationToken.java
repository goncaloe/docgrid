package com.docgrid.auth;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A identidade que o filtro de segurança extrai de um access token válido. */
class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final JwtService.AccessTokenClaims claims;

    JwtAuthenticationToken(JwtService.AccessTokenClaims claims) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
        this.claims = claims;
        setAuthenticated(true);
    }

    JwtService.AccessTokenClaims claims() {
        return claims;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return claims;
    }
}
