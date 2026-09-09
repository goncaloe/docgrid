package com.docgrid.auth;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A rede de segurança para quando não há identidade de sessão configurada.
 *
 * <p>No perfil {@code local}, {@link DevBootstrap} é o {@link CurrentUserProvider}. A
 * etapa 06 traz outro, baseado no token JWT. Sem nenhum dos dois — no perfil {@code aws}
 * antes da etapa 06, ou num teste de contexto — a aplicação continua a arrancar, mas
 * qualquer pedido que precise de saber quem é o utilizador falha alto, em vez de a
 * aplicação nem sequer subir.
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

        private static UnsupportedOperationException refuse() {
            return new UnsupportedOperationException(
                    "Sem identidade de sessão: usa o perfil 'local' ou espera pela autenticação da etapa 06");
        }
    }
}
