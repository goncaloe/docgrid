package com.docgrid.pipeline;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.docgrid.support.PostgresContainerConfiguration;

/** {@code /api/admin/dlq} é território de {@code ADMIN} — ver o {@code @PreAuthorize} em {@link DlqAdminController}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class DlqAdminAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @MockitoBean
    private DlqAdmin dlqAdmin;

    @Test
    void refusesAnAnonymousRequest() throws Exception {
        mvc.perform(get("/api/admin/dlq")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAUserWithoutTheAdminRole() throws Exception {
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.FINANCE);

        mvc.perform(get("/api/admin/dlq").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAnAdmin() throws Exception {
        when(dlqAdmin.list()).thenReturn(List.of());
        String token = jwt.accessToken(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        mvc.perform(get("/api/admin/dlq").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void refusesAMalformedToken() throws Exception {
        mvc.perform(get("/api/admin/dlq").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
