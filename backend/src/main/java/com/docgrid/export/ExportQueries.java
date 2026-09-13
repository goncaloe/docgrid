package com.docgrid.export;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas de preparação da exportação mensal: documentos aprovados pendentes e
 * indicadores do período.
 */
@Repository
class ExportQueries {

    private final JdbcClient jdbc;

    ExportQueries(JdbcClient jdbcClient) {
        this.jdbc = jdbcClient;
    }

    /**
     * Documentos aprovados sem exportação, com data de emissão anterior a {@code endExclusive}.
     * Cada linha sai já na forma que o CSV escreve — ver {@link ExportRow}.
     */
    List<ExportRow> selectApprovedDocumentsForExport(UUID organizationId, LocalDate endExclusive) {
        return jdbc.sql("""
                select d.id, d.issue_date, d.invoice_number, d.supplier_tax_id,
                       coalesce(f.value_text, s.name, d.supplier_tax_id) as supplier_name,
                       d.net_amount, d.vat_rate, d.vat_amount, d.total_amount,
                       d.category, d.currency, d.original_filename
                from documents d
                left join extracted_fields f
                    on f.document_id = d.id and f.field_name = 'SUPPLIER_NAME'
                left join suppliers s
                    on s.organization_id = d.organization_id and s.tax_id = d.supplier_tax_id
                where d.organization_id = :organizationId
                  and d.status = 'APPROVED'
                  and d.export_id is null
                  and d.issue_date is not null
                  and d.issue_date < :endExclusive
                order by d.issue_date, d.invoice_number
                """)
                .param("organizationId", organizationId)
                .param("endExclusive", endExclusive)
                .query((rs, n) -> new ExportRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("issue_date", LocalDate.class),
                        rs.getString("invoice_number"),
                        rs.getString("supplier_tax_id"),
                        rs.getString("supplier_name"),
                        rs.getBigDecimal("net_amount"),
                        rs.getBigDecimal("vat_rate"),
                        rs.getBigDecimal("vat_amount"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("category"),
                        rs.getString("currency"),
                        rs.getString("original_filename")))
                .list();
    }

    /**
     * Contagem de documentos aprovados sem {@code issue_date} — ficam retidos e não entram
     * em nenhuma exportação.
     */
    long countApprovedWithoutIssueDate(UUID organizationId) {
        return jdbc.sql("""
                select count(*)
                from documents
                where organization_id = :orgId
                  and status = 'APPROVED'
                  and issue_date is null
                """).param("orgId", organizationId).query(Long.class).single();
    }
}
