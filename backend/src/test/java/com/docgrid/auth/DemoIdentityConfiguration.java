package com.docgrid.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Dá aos testes de fluxo completo uma organização e um utilizador a quem atribuir os
 * documentos, e um {@link CurrentUserProvider} que os devolve — o mesmo que o perfil
 * {@code local} tem, mas sem depender desse perfil.
 *
 * <p>Reutiliza {@link DevBootstrap}: registado como bean, o Spring corre-lhe o
 * {@code ApplicationRunner} no arranque e ele semeia e guarda os ids.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DemoIdentityConfiguration {

    @Bean
    @Primary
    DevBootstrap demoIdentity(OrganizationRepository organizations, UserRepository users) {
        return new DevBootstrap(organizations, users);
    }
}
