package com.docgrid.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Autorização no endpoint do dashboard: anónimos, papel insuficiente e acesso financeiro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, DemoIdentityConfiguration.class})
@Transactional
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @Autowired
    private CurrentUserProvider currentUser;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void anonymousRequestReturns401() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeReturns403() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID employeeId = AuthFixtures.user(entityManager, organizationId, "employee@docgrid.local", UserRole.EMPLOYEE);
        String token = jwt.accessToken(employeeId, organizationId, UserRole.EMPLOYEE);

        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeReturns200() throws Exception {
        UUID userId = currentUser.currentUserId();
        UUID orgId = currentUser.currentOrganizationId();
        String token = jwt.accessToken(userId, orgId, UserRole.FINANCE);

        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }
}
