package com.docgrid.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.Actor;
import com.docgrid.export.dto.ExportResponse;
import com.docgrid.support.LocalStackContainerConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Testes de integração do {@link ExportService}: fecho de períodos contabilísticos com
 * Postgres real e S3 real (LocalStack).
 *
 * <p>Cada teste semeia os documentos que precisa via {@link JdbcTemplate} diretamente na
 * base de dados — sem passar pelo {@code DocumentService} — para ter controlo total sobre
 * datas, estados e valores. Cada teste usa um período diferente para evitar colisões na
 * exportação; o {@code @Transactional} do {@code ExportService} garante que cada chamada
 * a {@code create} corre na sua própria transação.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@Import({PostgresContainerConfiguration.class, LocalStackContainerConfiguration.class, DemoIdentityConfiguration.class})
class ExportServiceTest {

    @Autowired
    ExportService exportService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CurrentUserProvider currentUser;

    private UUID orgId;
    private UUID submitterId;

    @BeforeEach
    void setUp() {
        orgId = currentUser.currentOrganizationId();
        submitterId = currentUser.currentUserId();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private UUID anApprovedDocument(LocalDate issueDate) {
        return anApprovedDocument(issueDate, orgId);
    }

    private UUID anApprovedDocument(LocalDate issueDate, UUID organizationId) {
        UUID docId = UUID.randomUUID();
        jdbc.update(
                """
                insert into documents
                    (id, organization_id, submitted_by, storage_key, original_filename,
                     content_type, status, created_at, updated_at, supplier_tax_id,
                     invoice_number, issue_date, net_amount, vat_amount, vat_rate,
                     total_amount, currency)
                values (?, ?, ?, ?, ?, ?, 'APPROVED', now(), now(), ?, ?, ?, ?, ?, ?, ?, 'EUR')
                """,
                docId,
                organizationId,
                submitterId,
                "org/" + organizationId + "/test/" + docId + ".pdf",
                "fatura.pdf",
                "application/pdf",
                "505123452",
                "FT-" + docId.toString().substring(0, 8),
                issueDate,
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00"));
        // Regista a transição EXTRACTED → APPROVED no histórico, para que a auditoria
        // cubra o ciclo de vida completo (o ExportService cria depois o evento EXPORTED).
        jdbc.update("""
                insert into document_events
                    (document_id, event_type, from_status, to_status, actor_type,
                     actor_user_id, occurred_at)
                values (?, 'STATUS_CHANGED', 'EXTRACTED', 'APPROVED', 'SYSTEM', null, now())
                """, docId);
        return docId;
    }

    /** Cria uma organização e um utilizador extra, para testes de isolamento. */
    private void seedOtherOrganization(UUID otherOrgId, UUID otherUserId) {
        jdbc.update("""
                insert into organizations
                    (id, name, tax_id, approval_threshold, created_at, updated_at)
                values (?, 'Outra Organização Lda.', '123456789', 1000.00, now(), now())
                """, otherOrgId);
        jdbc.update("""
                insert into users
                    (id, organization_id, email, password_hash, full_name, role,
                     created_at, updated_at)
                values (?, ?, 'other@org.local', 'hash', 'Outro Utilizador', 'FINANCE',
                        now(), now())
                """, otherUserId, otherOrgId);
    }

    /** Cria um documento com estado {@code NEEDS_REVIEW}. */
    private UUID aNeedsReviewDocument() {
        UUID docId = UUID.randomUUID();
        jdbc.update(
                """
                insert into documents
                    (id, organization_id, submitted_by, storage_key, original_filename,
                     content_type, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'NEEDS_REVIEW', now(), now())
                """,
                docId,
                orgId,
                submitterId,
                "org/" + orgId + "/test/" + docId + ".pdf",
                "pendente.pdf",
                "application/pdf");
        return docId;
    }

    private static Actor actor() {
        return Actor.system();
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    void exportFuturePeriodThrows409() {
        var future = YearMonth.of(2099, 1);
        assertThatThrownBy(() -> exportService.create(orgId, future, actor()))
                .isInstanceOf(PeriodNotClosableException.class);
    }

    @Test
    void exportTwiceReturnsSameExport() {
        UUID doc1 = anApprovedDocument(LocalDate.of(2026, 8, 15));
        UUID doc2 = anApprovedDocument(LocalDate.of(2026, 8, 20));

        ExportResponse first = exportService.create(orgId, YearMonth.of(2026, 8), actor());
        ExportResponse second = exportService.create(orgId, YearMonth.of(2026, 8), actor());

        // Mesmo id, mesmos totais — o segundo pedido reencontra a exportação existente
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.documentCount()).isEqualTo(first.documentCount());

        // Cada documento tem exatamente um evento EXPORTED — a marcação não se repete
        assertThat(jdbc.queryForObject(
                        "select count(*) from document_events where document_id = ? and to_status = 'EXPORTED'",
                        Long.class,
                        doc1))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "select count(*) from document_events where document_id = ? and to_status = 'EXPORTED'",
                        Long.class,
                        doc2))
                .isOne();
    }

    @Test
    void exportMarksDocumentsAsExported() {
        UUID docId = anApprovedDocument(LocalDate.of(2026, 7, 15));

        ExportResponse response = exportService.create(orgId, YearMonth.of(2026, 7), actor());

        assertThat(response.documentCount()).isOne();

        String status = jdbc.queryForObject("select status from documents where id = ?", String.class, docId);
        UUID exportId = jdbc.queryForObject("select export_id from documents where id = ?", UUID.class, docId);

        assertThat(status).isEqualTo("EXPORTED");
        assertThat(exportId).isEqualTo(response.id());
    }

    @Test
    void documentsWithoutIssueDateAreExcludedAndCounted() {
        anApprovedDocument(null);

        ExportResponse response = exportService.create(orgId, YearMonth.of(2026, 6), actor());

        assertThat(response.documentCount()).isZero();
        assertThat(response.documentsWithoutDate()).isPositive();
    }

    @Test
    void otherOrganizationDataIsExcluded() {
        UUID otherOrgId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        seedOtherOrganization(otherOrgId, otherUserId);

        UUID otherDocId = anApprovedDocument(LocalDate.of(2026, 8, 15), otherOrgId);

        ExportResponse response = exportService.create(orgId, YearMonth.of(2026, 5), actor());

        // A exportação da nossa organização não inclui documentos alheios
        assertThat(response.documentCount()).isZero();

        // O documento da outra organização permanece APPROVED (inalterado)
        String status = jdbc.queryForObject("select status from documents where id = ?", String.class, otherDocId);
        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void needsReviewDocumentIsExcluded() {
        UUID docId = aNeedsReviewDocument();

        ExportResponse response = exportService.create(orgId, YearMonth.of(2026, 4), actor());

        // A exportação não inclui documentos que não estejam APPROVED
        assertThat(response.documentCount()).isZero();

        // O documento continua no seu estado original
        String status = jdbc.queryForObject("select status from documents where id = ?", String.class, docId);
        assertThat(status).isEqualTo("NEEDS_REVIEW");
    }
}
