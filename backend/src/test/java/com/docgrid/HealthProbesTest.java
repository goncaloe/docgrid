package com.docgrid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * As probes que o container e a orquestração usam (Dockerfile, compose do ambiente AWS):
 * a readiness inclui o db — a aplicação não serve sem base de dados — e exclui de
 * propósito o s3, o sqs e o extrator. Um soluço transitório num deles não pode matar
 * um container que está pronto a servir; o caminho é público para a probe poder bater
 * sem token (SecurityConfig), os detalhes continuam atrás de when-authorized.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureObservability
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class HealthProbesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @Test
    void readinessIncludesDbButNotTheAwsDependencies() throws Exception {
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        mvc.perform(get("/actuator/health/readiness").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.s3").doesNotExist())
                .andExpect(jsonPath("$.components.sqs").doesNotExist())
                .andExpect(jsonPath("$.components.extractor").doesNotExist());
    }

    @Test
    void readinessIsReachableWithoutAToken() throws Exception {
        // A probe do container não pode autenticar; só o estado, sem detalhes.
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
