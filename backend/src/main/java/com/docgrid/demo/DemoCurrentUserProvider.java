package com.docgrid.demo;

import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoUserFactory;
import com.docgrid.auth.UserRole;

/**
 * Quem o seed está a ser, em cada momento.
 *
 * <p>Os serviços de domínio perguntam ao {@link CurrentUserProvider} quem faz o pedido, e
 * a implementação de produção lê essa resposta das claims de um token JWT. O seed não faz
 * pedidos HTTP — entra pelo domínio — por isso troca essa fonte por uma identidade que ele
 * próprio aponta: submete como o empregado, corrige como o {@code FINANCE}, aprova a
 * despesa grande como o {@code MANAGER}. Sem isto, os sessenta documentos apareciam todos
 * submetidos e aprovados pela mesma pessoa, e o histórico de auditoria não mostrava nada.
 *
 * <p>O molde é o {@code DemoIdentityConfiguration} dos testes
 * ({@code src/test/java/com/docgrid/auth/}); a diferença é que aqui a identidade muda ao
 * longo da execução, em vez de ser fixa.
 */
@Component
@Profile("demo")
@Primary
class DemoCurrentUserProvider implements CurrentUserProvider {

    private volatile DemoUserFactory.DemoUser acting;

    /** Passa a agir como este utilizador. Tudo o que o seed fizer a seguir é dele. */
    void actAs(DemoUserFactory.DemoUser user) {
        this.acting = user;
    }

    @Override
    public UUID currentOrganizationId() {
        return acting().organizationId();
    }

    @Override
    public UUID currentUserId() {
        return acting().userId();
    }

    @Override
    public UserRole currentRole() {
        return acting().role();
    }

    private DemoUserFactory.DemoUser acting() {
        DemoUserFactory.DemoUser user = acting;
        if (user == null) {
            throw new IllegalStateException("O seed ainda não disse por quem está a agir");
        }
        return user;
    }
}
