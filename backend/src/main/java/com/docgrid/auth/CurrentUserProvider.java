package com.docgrid.auth;

import java.util.UUID;

/**
 * Quem está a fazer o pedido: a organização, o utilizador e o seu papel.
 *
 * <p>A implementação de produção ({@code AuthenticatedCurrentUserProvider}) lê estes três
 * valores das claims do token JWT já validado pelo filtro de segurança.
 */
public interface CurrentUserProvider {

    UUID currentOrganizationId();

    UUID currentUserId();

    UserRole currentRole();
}
