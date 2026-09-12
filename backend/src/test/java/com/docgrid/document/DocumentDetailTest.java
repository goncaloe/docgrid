package com.docgrid.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.auth.JwtTestSupport;
import com.docgrid.auth.UserRole;
import com.docgrid.extraction.FieldGeometry;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O detalhe de um documento servido como a aplicação o serve de facto: <b>sem</b>
 * {@code @Transactional} na classe de teste.
 *
 * <p>É essa a razão de ser desta classe existir à parte de {@link DocumentControllerTest}.
 * A transação de teste mantém a sessão do Hibernate aberta do princípio ao fim, e com ela
 * aberta a coleção {@code LAZY} de campos extraídos carrega sempre — mesmo quando em
 * produção, onde o pedido corre sem transação nenhuma, o mapeamento da resposta rebentava
 * com {@code LazyInitializationException}. O endpoint esteve partido desde a etapa 06 e a
 * suite inteira passava.
 *
 * <p>Sem transação de teste também não há rollback: cada teste cria a sua própria
 * organização, com id novo, e não vê o que os outros deixaram.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class DocumentDetailTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTestSupport jwt;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private TransactionTemplate transactions;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void theDetailBringsTheExtractedFieldsWithNoTransactionOpen() throws Exception {
        UUID organizationId = transactions.execute(status -> AuthFixtures.organization(entityManager));
        UUID finance = transactions.execute(status -> AuthFixtures.user(
                entityManager, organizationId, "revisora-" + UUID.randomUUID() + "@padaria.pt", UserRole.FINANCE));
        UUID documentId = transactions.execute(status -> anExtractedDocument(organizationId, finance));

        mvc.perform(get("/api/documents/" + documentId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwt.accessToken(finance, organizationId, UserRole.FINANCE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fields").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.fields[0].page").value(1))
                .andExpect(jsonPath("$.fields[0].boundingBox").value(org.hamcrest.Matchers.hasSize(4)));
    }

    /**
     * Um documento por processar não tem {@code file_hash}. A consulta de duplicado por
     * hash corre à mesma no detalhe: se tratasse o nulo como um valor a comparar, qualquer
     * outro documento ainda por processar da organização apareceria como duplicado.
     */
    @Test
    void aDocumentWithNoFileHashYetIsNobodysDuplicate() throws Exception {
        UUID organizationId = transactions.execute(status -> AuthFixtures.organization(entityManager));
        UUID finance = transactions.execute(status -> AuthFixtures.user(
                entityManager, organizationId, "revisora-" + UUID.randomUUID() + "@padaria.pt", UserRole.FINANCE));
        UUID first = transactions.execute(status -> anUploadedDocument(organizationId, finance));
        UUID second = transactions.execute(status -> anUploadedDocument(organizationId, finance));

        String token = jwt.accessToken(finance, organizationId, UserRole.FINANCE);
        mvc.perform(get("/api/documents/" + second).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOfDocumentId").doesNotExist());
        mvc.perform(get("/api/documents/" + first).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOfDocumentId").doesNotExist());
    }

    private UUID anExtractedDocument(UUID organizationId, UUID submittedBy) {
        Document document = newDocument(organizationId, submittedBy);
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        ExtractedField.readByMachine(
                document,
                ExtractedFieldName.TOTAL_AMOUNT,
                "123.00",
                new BigDecimal("0.44"),
                new FieldGeometry(
                        1,
                        List.of(
                                new FieldGeometry.Point(0.6, 0.55),
                                new FieldGeometry.Point(0.9, 0.55),
                                new FieldGeometry.Point(0.9, 0.59),
                                new FieldGeometry.Point(0.6, 0.59))));
        document.projectInvoiceFields(new InvoiceFields(
                "Cantina do Zé, Lda.",
                "505123452",
                "FT 2026/" + UUID.randomUUID(),
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        return documents.save(document).getId();
    }

    private UUID anUploadedDocument(UUID organizationId, UUID submittedBy) {
        return documents.save(newDocument(organizationId, submittedBy)).getId();
    }

    private Document newDocument(UUID organizationId, UUID submittedBy) {
        return new Document(
                organizationId,
                submittedBy,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
    }
}
