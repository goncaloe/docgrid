package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Autorização e isolamento por organização sobre a API de documentos: os critérios de
 * aceitação da etapa 06 a sério, contra Postgres e segurança reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
@Transactional
class DocumentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @Autowired
    private DocumentRepository documents;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void anAnonymousRequestIsRefused() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmployeeSeesOnlyTheirOwnDocuments() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID employee1 = AuthFixtures.user(entityManager, organizationId, "um@padaria.pt", UserRole.EMPLOYEE);
        UUID employee2 = AuthFixtures.user(entityManager, organizationId, "dois@padaria.pt", UserRole.EMPLOYEE);
        UUID documentId = anExtractedDocument(organizationId, employee1);

        String tokenEmployee1 = jwt.accessToken(employee1, organizationId, UserRole.EMPLOYEE);
        String tokenEmployee2 = jwt.accessToken(employee2, organizationId, UserRole.EMPLOYEE);

        mvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmployee1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));

        mvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmployee2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)));

        mvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmployee2))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserFromAnotherOrganizationGetsNotFoundNotForbidden() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID submitter = AuthFixtures.user(entityManager, organizationId, "um@padaria.pt", UserRole.FINANCE);
        UUID documentId = anExtractedDocument(organizationId, submitter);

        UUID otherOrganizationId = AuthFixtures.organization(entityManager);
        UUID otherUser = AuthFixtures.user(entityManager, otherOrganizationId, "outra@empresa.pt", UserRole.FINANCE);
        String token = jwt.accessToken(otherUser, otherOrganizationId, UserRole.FINANCE);

        mvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeCannotCorrectAField() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID employee = AuthFixtures.user(entityManager, organizationId, "um@padaria.pt", UserRole.EMPLOYEE);
        UUID documentId = anExtractedDocument(organizationId, employee);
        String token = jwt.accessToken(employee, organizationId, UserRole.EMPLOYEE);

        mvc.perform(patch("/api/documents/" + documentId + "/fields/SUPPLIER_TAX_ID")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value": "505123452"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCorrectsAFieldWithHumanOriginAndItSticks() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID finance = AuthFixtures.user(entityManager, organizationId, "financas@padaria.pt", UserRole.FINANCE);
        UUID documentId = anExtractedDocument(organizationId, finance);
        String token = jwt.accessToken(finance, organizationId, UserRole.FINANCE);

        mvc.perform(patch("/api/documents/" + documentId + "/fields/INVOICE_NUMBER")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value": "FT 2026/99"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("FT 2026/99"))
                .andExpect(jsonPath("$.fields[?(@.fieldName == 'INVOICE_NUMBER')].source")
                        .value(org.hamcrest.Matchers.contains("HUMAN")));
    }

    @Test
    void onlyAManagerApprovesAboveTheThreshold() throws Exception {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID finance = AuthFixtures.user(entityManager, organizationId, "financas@padaria.pt", UserRole.FINANCE);
        UUID manager = AuthFixtures.user(entityManager, organizationId, "gestor@padaria.pt", UserRole.MANAGER);
        UUID documentId = anAboveThresholdDocument(organizationId, finance);

        String financeToken = jwt.accessToken(finance, organizationId, UserRole.FINANCE);
        String managerToken = jwt.accessToken(manager, organizationId, UserRole.MANAGER);

        mvc.perform(post("/api/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    }

    private UUID anExtractedDocument(UUID organizationId, UUID submittedBy) {
        Document document = new Document(
                organizationId,
                submittedBy,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                "Cantina do Zé, Lda.",
                "505123452",
                "FT 2026/1",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        return documents.save(document).getId();
    }

    private UUID anAboveThresholdDocument(UUID organizationId, UUID submittedBy) {
        Document document = new Document(
                organizationId,
                submittedBy,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura-grande.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                "Fornecedor Grande, Lda.",
                "505123452",
                "FT 2026/2",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("2000.00"),
                new BigDecimal("460.00"),
                new BigDecimal("23.00"),
                new BigDecimal("2460.00")));
        return documents.save(document).getId();
    }
}
