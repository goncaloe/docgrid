package com.docgrid.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O critério "o health reporta cada dependência separadamente", com a infraestrutura a
 * correr: Postgres no container e S3+SQS no LocalStack (como em {@code PipelineFlowTest})
 * — cada componente diz UP no seu nome, e o db continua a ser o do Boot.
 *
 * <p>O MockMvc monta-se à mão (em vez de {@code @AutoConfigureMockMvc}) para repetir
 * exatamente as anotações de {@code PipelineFlowTest}: qualquer diferença criaria um
 * contexto novo, e contexto novo é LocalStack novo. Sem o {@code AutoConfigureMockMvc} a
 * segurança não entra no MockMvc à mão, e sem ela o {@code when-authorized} do health não
 * vê o ADMIN e esconde os detalhes — por isso se junta o {@code springSecurityFilterChain}
 * como filter explícito.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTestSupport jwt;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void eachDependencyReportsSeparatelyAndUp() throws Exception {
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        mvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.s3.status").value("UP"))
                .andExpect(jsonPath("$.components.sqs.status").value("UP"))
                .andExpect(jsonPath("$.components.extractor.status").value("UP"));
    }
}
