package com.docgrid.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Teste de desempenho para as queries do dashboard com 5000 documentos semeados em
 * lote. A primeira chamada aquece o caches/planos, a segunda é cronometrada e deve
 * ficar abaixo de 1000 ms.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, DemoIdentityConfiguration.class})
@Transactional
class DashboardPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(DashboardPerformanceTest.class);
    private static final String SUPPLIER_TAX_ID = "505123452";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CurrentUserProvider currentUser;

    @Autowired
    private DashboardQueries queries;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = currentUser.currentOrganizationId();
        UUID submitterId = currentUser.currentUserId();

        jdbc.batchUpdate("""
                insert into documents
                    (id, organization_id, submitted_by, storage_key,
                     original_filename, content_type, status, created_at, updated_at,
                     supplier_tax_id, invoice_number, issue_date,
                     net_amount, vat_amount, vat_rate, total_amount, category, currency)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                UUID id = UUID.randomUUID();
                int month = (i % 12) + 1;
                int day = (i % 28) + 1;
                LocalDate issueDate = LocalDate.of(2026, month, day);

                // ~80% APPROVED, ~10% EXPORTED, ~10% NEEDS_REVIEW
                String status;
                if (i % 10 == 0) {
                    status = "NEEDS_REVIEW";
                } else if (i % 10 == 5) {
                    status = "EXPORTED";
                } else {
                    status = "APPROVED";
                }

                String[] categories = {"Alimentação", "Manutenção", "Serviços", "Equipamento", "Consultoria", null};
                String category = categories[i % categories.length];
                String supplier = (i % 20 == 0) ? "123456789" : SUPPLIER_TAX_ID;

                BigDecimal net = BigDecimal.valueOf(100.00 + (i % 50) * 10.0);
                BigDecimal vat = net.multiply(new BigDecimal("0.23")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal total = net.add(vat);

                ps.setObject(1, id);
                ps.setObject(2, orgId);
                ps.setObject(3, submitterId);
                ps.setString(4, "org/" + orgId + "/2026/" + id + ".pdf");
                ps.setString(5, "fatura-" + id.toString().substring(0, 8) + ".pdf");
                ps.setString(6, "application/pdf");
                ps.setString(7, status);
                ps.setObject(8, LocalDate.of(2026, 1, 1).atStartOfDay());
                ps.setObject(9, LocalDate.of(2026, 1, 1).atStartOfDay());
                ps.setString(10, supplier);
                ps.setString(11, "FT-" + (10000 + i));
                ps.setObject(12, issueDate);
                ps.setBigDecimal(13, net);
                ps.setBigDecimal(14, vat);
                ps.setBigDecimal(15, new BigDecimal("23.00"));
                ps.setBigDecimal(16, total);
                if (category != null) {
                    ps.setString(17, category);
                } else {
                    ps.setNull(17, Types.VARCHAR);
                }
                ps.setString(18, "EUR");
            }

            @Override
            public int getBatchSize() {
                return 5000;
            }
        });
    }

    @Test
    void dashboardPerformanceUnder1000Ms() {
        YearMonth from = YearMonth.of(2026, 1);
        YearMonth to = YearMonth.of(2026, 12);

        // Warmup — aquece caches, planos de execução, etc.
        queries.periodTotals(orgId, from, to);

        // Medição
        long start = System.nanoTime();
        queries.periodTotals(orgId, from, to);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        log.info("Dashboard periodTotals with 5000 docs: {} ms", elapsedMs);
        assertThat(elapsedMs).isLessThan(1000L);
    }
}
