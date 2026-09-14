package com.docgrid.pipeline;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.pipeline.dto.PipelineStats;
import com.docgrid.support.PostgresContainerConfiguration;

/** {@code /api/admin/pipeline/stats} é território de {@code ADMIN} — ver o {@code @PreAuthorize}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class PipelineStatsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @MockitoBean
    private PipelineStatsService stats;

    @Test
    void refusesAnAnonymousRequest() throws Exception {
        mvc.perform(get("/api/admin/pipeline/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAUserWithoutTheAdminRole() throws Exception {
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.FINANCE);

        mvc.perform(get("/api/admin/pipeline/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAnAdminAndReturnsTheStats() throws Exception {
        when(stats.current())
                .thenReturn(new PipelineStats(
                        Map.of("UPLOADED", 2L, "EXTRACTED", 1L),
                        new PipelineStats.Queue("docgrid-document-processing", 3, 1),
                        new PipelineStats.Queue("docgrid-document-processing-dlq", 0, 0)));
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        mvc.perform(get("/api/admin/pipeline/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsByStatus.UPLOADED").value(2))
                .andExpect(jsonPath("$.documentsByStatus.EXTRACTED").value(1))
                .andExpect(jsonPath("$.main.name").value("docgrid-document-processing"))
                .andExpect(jsonPath("$.main.available").value(3))
                .andExpect(jsonPath("$.main.inFlight").value(1))
                .andExpect(jsonPath("$.dlq.available").value(0))
                .andExpect(jsonPath("$.dlq.inFlight").value(0));
    }
}
