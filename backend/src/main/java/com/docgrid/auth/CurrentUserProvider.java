package com.docgrid.auth;

import java.util.UUID;

/**
 * Quem está a fazer o pedido: a organização e o utilizador.
 *
 * <p>Até à etapa 06 não há autenticação. Em desenvolvimento local, {@link DevBootstrap}
 * implementa isto com o utilizador de demonstração que semeia no arranque. A etapa 06
 * acrescenta uma implementação que lê o token JWT do pedido e remove a de demonstração —
 * esta interface, e quem a usa, ficam na mesma.
 */
public interface CurrentUserProvider {

    UUID currentOrganizationId();

    UUID currentUserId();
}
