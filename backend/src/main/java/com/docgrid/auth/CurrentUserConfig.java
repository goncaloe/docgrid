package com.docgrid.auth;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A rede de segurança para quando não há identidade de sessão configurada.
 *
 * <p>{@code AuthenticatedCurrentUserProvider} lê a identidade do token JWT validado pelo
 * filtro de segurança. Sem um pedido autenticado por trás — num teste de contexto que não
 * levanta segurança, por exemplo — a aplicação continua a arrancar, mas qualquer chamada
 * que precise de saber quem é o utilizador falha alto, em vez de a aplicação nem sequer
 * subir.
 */
@Configuration(proxyBeanMethods = false)
class CurrentUserConfig {

    @Bean
    @ConditionalOnMissingBean
    CurrentUserProvider unauthenticatedCurrentUserProvider() {
        return new Unauthenticated();
    }

    private static final class Unauthenticated implements CurrentUserProvider {

        @Override
        public UUID currentOrganizationId() {
            throw refuse();
        }

        @Override
        public UUID currentUserId() {
            throw refuse();
        }

        @Override
        public UserRole currentRole() {
            throw refuse();
        }

        private static UnsupportedOperationException refuse() {
            return new UnsupportedOperationException("Sem identidade de sessão: pedido não autenticado");
        }
    }
}
