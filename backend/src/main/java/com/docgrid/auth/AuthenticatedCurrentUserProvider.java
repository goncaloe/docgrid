package com.docgrid.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Lê a identidade das claims do access token já validado pelo {@link JwtAuthenticationFilter}. */
@Component
class AuthenticatedCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID currentOrganizationId() {
        return claims().organizationId();
    }

    @Override
    public UUID currentUserId() {
        return claims().userId();
    }

    @Override
    public UserRole currentRole() {
        return claims().role();
    }

    private JwtService.AccessTokenClaims claims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Sem identidade de sessão autenticada");
        }
        return token.claims();
    }
}
