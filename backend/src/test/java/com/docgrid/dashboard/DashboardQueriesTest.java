package com.docgrid.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.dashboard.dto.DashboardResponse.CategoryTotal;
import com.docgrid.dashboard.dto.DashboardResponse.MonthlyTotal;
import com.docgrid.dashboard.dto.DashboardResponse.Operations;
import com.docgrid.dashboard.dto.DashboardResponse.PeriodTotals;
import com.docgrid.dashboard.dto.DashboardResponse.SupplierTotal;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Testes de integração para as agregações do {@link DashboardQueries}: totais do
 * período, indicadores operacionais, séries mensais, categorias e top fornecedores.
 * Semeia dados conhecidos via {@link JdbcTemplate} e verifica os valores exactos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, DemoIdentityConfiguration.class})
@Transactional
class DashboardQueriesTest {

    @Autowired
    private DashboardQueries queries;

    @Autowired
    private CurrentUserProvider currentUser;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    private final UUID orgId = UUID.randomUUID();
    private final UUID otherOrgId = UUID.randomUUID();
    private static final String SUPPLIER_TAX_ID = "505123452";
    private static final YearMonth FROM = YearMonth.of(2026, 8);
    private static final YearMonth TO = YearMonth.of(2026, 9);

    private UUID doc1Id;
    private UUID doc2Id;
    private UUID doc3Id;
    private UUID doc4Id;
    private UUID doc5Id;

    @BeforeEach
    void setUp() {
        UUID submitterId = currentUser.currentUserId();

        doc1Id = UUID.randomUUID();
        doc2Id = UUID.randomUUID();
        doc3Id = UUID.randomUUID();
        doc4Id = UUID.randomUUID();
        doc5Id = UUID.randomUUID();

        // Organizações próprias do teste, ambas criadas aqui. A da identidade demo não
        // serve: é partilhada com testes que fazem commit (DocumentApprovalServiceTest,
        // PipelineFlowTest, ...) e estas queries agregam tudo o que estiver na
        // organização, pelo que os totais exactos dependeriam da ordem de execução.
        // Da identidade demo só se aproveita o utilizador, para satisfazer a FK
        // submitted_by.
        insertOrganization(orgId, "Padaria do Bairro, Lda.", "501442889");
        insertOrganization(otherOrgId, "Outra Empresa, Lda.", "999999999");

        // ── 4 documentos na organização corrente ──────────────────────────
        //
        // Doc 1: APPOVED, "Alimentação",  100.00 total,  issue_date 2026-08-15
        // Doc 2: EXPORTED, "Alimentação",  200.00 total,  issue_date 2026-08-20
        // Doc 3: APPROVED, "Manutenção",    50.00 total,  issue_date 2026-09-05
        //
        // Doc 4: noutra organização — nunca deve aparecer nas queries da org actual
        //
        // Doc 5: APPROVED via fluxo de revisão (teve NEEDS_REVIEW antes)
        //        123.00 total,  issue_date 2026-08-20,  category null

        insertDocument(
                doc1Id,
                orgId,
                submitterId,
                "APPROVED",
                LocalDate.of(2026, 8, 15),
                "Alimentação",
                "FT-001",
                new BigDecimal("80.00"),
                new BigDecimal("20.00"),
                new BigDecimal("25.00"),
                new BigDecimal("100.00"));

        insertDocument(
                doc2Id,
                orgId,
                submitterId,
                "EXPORTED",
                LocalDate.of(2026, 8, 20),
                "Alimentação",
                "FT-002",
                new BigDecimal("160.00"),
                new BigDecimal("40.00"),
                new BigDecimal("25.00"),
                new BigDecimal("200.00"));

        insertDocument(
                doc3Id,
                orgId,
                submitterId,
                "APPROVED",
                LocalDate.of(2026, 9, 5),
                "Manutenção",
                "FT-003",
                new BigDecimal("40.00"),
                new BigDecimal("10.00"),
                new BigDecimal("25.00"),
                new BigDecimal("50.00"));

        insertDocument(
                doc4Id,
                otherOrgId,
                submitterId,
                "APPROVED",
                LocalDate.of(2026, 8, 10),
                "Alimentação",
                "FT-004",
                new BigDecimal("200.00"),
                new BigDecimal("46.00"),
                new BigDecimal("23.00"),
                new BigDecimal("246.00"));

        insertDocument(
                doc5Id,
                orgId,
                submitterId,
                "APPROVED",
                LocalDate.of(2026, 8, 20),
                null,
                "FT-005",
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00"));

        // Eventos de revisão para o doc 5: teve NEEDS_REVIEW antes de APPROVED
        jdbcTemplate.update(
                """
                insert into document_events
                    (document_id, event_type, from_status, to_status, actor_type, actor_user_id, occurred_at)
                values (?, 'STATUS_CHANGED', 'EXTRACTED', 'NEEDS_REVIEW', 'SYSTEM', null, ?),
                       (?, 'STATUS_CHANGED', 'NEEDS_REVIEW', 'APPROVED', 'USER', ?, ?)
                """,
                doc5Id,
                LocalDateTime.of(2026, 8, 21, 10, 0),
                doc5Id,
                submitterId,
                LocalDateTime.of(2026, 8, 21, 11, 30));

        em.flush();
    }

    private void insertOrganization(UUID id, String name, String taxId) {
        jdbcTemplate.update("""
                insert into organizations (id, name, tax_id, approval_threshold, created_at, updated_at)
                values (?, ?, ?, 1000.00, now(), now())
                """, id, name, taxId);
    }

    private void insertDocument(
            UUID id,
            UUID organizationId,
            UUID submittedBy,
            String status,
            LocalDate issueDate,
            String category,
            String invoiceNumber,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal vatRate,
            BigDecimal totalAmount) {
        jdbcTemplate.update(
                """
                insert into documents
                    (id, organization_id, submitted_by, storage_key,
                     original_filename, content_type, status, created_at, updated_at,
                     supplier_tax_id, invoice_number, issue_date,
                     net_amount, vat_amount, vat_rate, total_amount, category, currency)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                organizationId,
                submittedBy,
                "org/" + organizationId + "/2026/" + id + ".pdf",
                "fatura-" + id.toString().substring(0, 8) + ".pdf",
                "application/pdf",
                status,
                LocalDate.of(2026, 8, 1).atStartOfDay(),
                LocalDate.of(2026, 8, 1).atStartOfDay(),
                SUPPLIER_TAX_ID,
                invoiceNumber,
                issueDate,
                netAmount,
                vatAmount,
                vatRate,
                totalAmount,
                category,
                "EUR");
    }

    @Test
    void periodTotalsMatchesKnownData() {
        PeriodTotals result = queries.periodTotals(orgId, FROM, TO);

        // 4 documentos no período: doc1 (100.00) + doc2 (200.00) + doc3 (50.00) + doc5 (123.00)
        assertThat(result.documents()).isEqualTo(4);
        assertThat(result.net()).isEqualByComparingTo("380.00");
        assertThat(result.vat()).isEqualByComparingTo("93.00");
        assertThat(result.total()).isEqualByComparingTo("473.00");
    }

    @Test
    void operationsReturnsCorrectAutomationRate() {
        Operations result = queries.operations(orgId, FROM, TO);

        // 4 documentos processados (todos na org, nenhum UPLOADED/PROCESSING/FAILED)
        assertThat(result.processedDocuments()).isEqualTo(4);

        // Apenas o doc 5 teve revisão humana (NEEDS_REVIEW) → 3/4 = 0.75
        assertThat(result.automationRate()).isEqualByComparingTo("0.75");
    }

    @Test
    void monthlyTotalsGroupsCorrectly() {
        List<MonthlyTotal> results = queries.monthlyTotals(orgId, FROM, TO);

        assertThat(results).hasSize(2);

        // Agosto: doc1 (100) + doc2 (200) + doc5 (123)
        assertThat(results.get(0).month()).isEqualTo("2026-08");
        assertThat(results.get(0).documents()).isEqualTo(3);
        assertThat(results.get(0).net()).isEqualByComparingTo("340.00");
        assertThat(results.get(0).vat()).isEqualByComparingTo("83.00");
        assertThat(results.get(0).total()).isEqualByComparingTo("423.00");

        // Setembro: doc3 (50)
        assertThat(results.get(1).month()).isEqualTo("2026-09");
        assertThat(results.get(1).documents()).isEqualTo(1);
        assertThat(results.get(1).net()).isEqualByComparingTo("40.00");
        assertThat(results.get(1).vat()).isEqualByComparingTo("10.00");
        assertThat(results.get(1).total()).isEqualByComparingTo("50.00");
    }

    @Test
    void categoryTotalsGroupsCorrectly() {
        List<CategoryTotal> results = queries.categoryTotals(orgId, FROM, TO);

        assertThat(results).hasSize(3);

        // Alimentação: doc1 (100) + doc2 (200) = 300
        assertThat(results.get(0).category()).isEqualTo("Alimentação");
        assertThat(results.get(0).documents()).isEqualTo(2);
        assertThat(results.get(0).total()).isEqualByComparingTo("300.00");

        // null category: doc5 (123) — ordenado por total desc, fica em segundo
        assertThat(results.get(1).category()).isNull();
        assertThat(results.get(1).documents()).isEqualTo(1);
        assertThat(results.get(1).total()).isEqualByComparingTo("123.00");

        // Manutenção: doc3 (50)
        assertThat(results.get(2).category()).isEqualTo("Manutenção");
        assertThat(results.get(2).documents()).isEqualTo(1);
        assertThat(results.get(2).total()).isEqualByComparingTo("50.00");
    }

    @Test
    void topSuppliersReturnsLimitedResults() {
        List<SupplierTotal> results = queries.topSuppliers(orgId, FROM, TO, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).taxId()).isEqualTo(SUPPLIER_TAX_ID);
        assertThat(results.get(0).name()).isEqualTo(SUPPLIER_TAX_ID); // sem supplier cadastrado
        assertThat(results.get(0).documents()).isEqualTo(4);
        assertThat(results.get(0).total()).isEqualByComparingTo("473.00");
    }

    @Test
    void otherOrganizationDataIsExcluded() {
        // Query da org principal não vê o doc da outra organização
        PeriodTotals own = queries.periodTotals(orgId, FROM, TO);
        assertThat(own.documents()).isEqualTo(4);

        // Query exclusiva da outra organização vê apenas o doc dela
        PeriodTotals other = queries.periodTotals(otherOrgId, FROM, TO);
        assertThat(other.documents()).isEqualTo(1);
        assertThat(other.total()).isEqualByComparingTo("246.00");
    }
}
