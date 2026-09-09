package com.docgrid.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * O Postgres dos testes de integração, como bean.
 *
 * <p>Um container por contexto Spring, e não um por classe de teste: o Spring reaproveita
 * o contexto entre classes com a mesma configuração, portanto todas as classes de
 * repositório partilham o mesmo Postgres. Nunca H2 — um teste que passa em H2 e falha em
 * Postgres não vale nada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
