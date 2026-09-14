package com.docgrid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Prova que a aplicação arranca: contexto Spring, Flyway aplicado num Postgres real e o
 * endpoint de saúde a responder.
 *
 * <p>Com {@code ddl-auto: validate}, este teste é também a prova de que as entidades JPA e
 * as migrações dizem a mesma coisa — se divergirem, o contexto não sobe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class ApplicationContextTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void healthEndpointResponds() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        // O endpoint responde sempre; o estado depende das dependências. Num contexto
        // sem LocalStack o S3 e o SQS estão em baixo e o estado geral é DOWN (o health
        // reflete isso, ver ADR 0016). O que se afirma é que o actuator responde — nunca
        // 404 nem 401 — e que o corpo é um health JSON válido.
        assertThat(response.getStatusCode().value()).isIn(200, 503);
        assertThat(response.getBody()).isIn("{\"status\":\"UP\"}", "{\"status\":\"DOWN\"}");
    }
}
