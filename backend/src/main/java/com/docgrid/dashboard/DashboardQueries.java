package com.docgrid.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.docgrid.dashboard.dto.DashboardResponse.CategoryTotal;
import com.docgrid.dashboard.dto.DashboardResponse.MonthlyTotal;
import com.docgrid.dashboard.dto.DashboardResponse.Operations;
import com.docgrid.dashboard.dto.DashboardResponse.PeriodTotals;
import com.docgrid.dashboard.dto.DashboardResponse.SupplierTotal;

/**
 * Agregações SQL do dashboard — totais, séries, categorias, fornecedores e indicadores
 * operacionais. {@link org.springframework.transaction.annotation.Transactional
 * Transactional} fica a cargo de quem chama.
 */
@Repository
class DashboardQueries {

    private final JdbcClient jdbc;

    DashboardQueries(JdbcClient jdbcClient) {
        this.jdbc = jdbcClient;
    }

    /**
     * Totais do período: soma de documentos, líquido, IVA e total (apenas documentos
     * {@code APPROVED} ou {@code EXPORTED}, filtrados por {@code issue_date}).
     */
    PeriodTotals periodTotals(UUID organizationId, YearMonth from, YearMonth to) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        return jdbc.sql("""
                select coalesce(count(*), 0)::bigint         as documents,
                       coalesce(sum(net_amount), 0)          as net,
                       coalesce(sum(vat_amount), 0)          as vat,
                       coalesce(sum(total_amount), 0)        as total
                from documents
                where organization_id = :orgId
                  and status in ('APPROVED', 'EXPORTED')
                  and issue_date between :fromDate and :toDate
                """)
                .param("orgId", organizationId)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .query((rs, n) -> new PeriodTotals(
                        rs.getLong("documents"),
                        rs.getBigDecimal("net"),
                        rs.getBigDecimal("vat"),
                        rs.getBigDecimal("total")))
                .single();
    }

    /**
     * Indicadores operacionais: documentos processados, taxa de automação e tempo médio
     * de revisão em segundos, filtrados por {@code created_at} do documento.
     *
     * <ul>
     *   <li>Processados: documentos cujo estado não é {@code UPLOADED}, {@code PROCESSING}
     *       nem {@code FAILED}.
     *   <li>Taxa de automação: fração de processados que nunca tiveram
     *       {@code NEEDS_REVIEW} nem {@code FIELD_CORRECTED}.
     *   <li>Tempo médio de revisão: média de segundos entre a primeira passagem a
     *       {@code NEEDS_REVIEW} e a decisão ({@code APPROVED} ou {@code REJECTED}).
     * </ul>
     */
    Operations operations(UUID organizationId, YearMonth from, YearMonth to) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        return jdbc.sql("""
                with doc_filter as (
                    select id, status
                    from documents
                    where organization_id = :orgId
                      and created_at >= :fromDate
                      and created_at < :toDatePlus1
                ),
                processed as (
                    select id from doc_filter
                    where status not in ('UPLOADED', 'PROCESSING', 'FAILED')
                ),
                automated as (
                    select p.id
                    from processed p
                    where not exists (
                        select 1 from document_events de
                        where de.document_id = p.id
                          and (de.event_type = 'FIELD_CORRECTED'
                               or (de.event_type = 'STATUS_CHANGED' and de.to_status = 'NEEDS_REVIEW'))
                    )
                ),
                review_cycles as (
                    select
                        de.document_id,
                        de.occurred_at as review_start,
                        min(de2.occurred_at) as decision_time
                    from document_events de
                    join document_events de2 on de2.document_id = de.document_id
                        and de2.occurred_at > de.occurred_at
                        and de2.to_status in ('APPROVED', 'REJECTED')
                    where de.document_id in (select id from processed)
                      and de.to_status = 'NEEDS_REVIEW'
                    group by de.document_id, de.occurred_at
                )
                select
                    (select count(*) from processed)::bigint               as processed_documents,
                    (select case
                        when (select count(*) from processed) > 0
                        then (select count(*)::numeric from automated)
                           / (select count(*)::numeric from processed)
                        else null
                    end)                                                    as automation_rate,
                    (select avg(extract(epoch from (decision_time - review_start)))::bigint
                     from review_cycles)                                    as average_review_seconds
                """)
                .param("orgId", organizationId)
                .param("fromDate", fromDate)
                .param("toDatePlus1", toDate.plusDays(1))
                .query((rs, n) -> new Operations(
                        rs.getLong("processed_documents"),
                        rs.getBigDecimal("automation_rate"),
                        rs.getObject("average_review_seconds", Long.class)))
                .single();
    }

    /**
     * Série mensal dos totais, agrupada por mês do {@code issue_date}.
     */
    List<MonthlyTotal> monthlyTotals(UUID organizationId, YearMonth from, YearMonth to) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        return jdbc.sql("""
                select to_char(issue_date, 'YYYY-MM')                     as month,
                       count(*)::bigint                                    as documents,
                       coalesce(sum(net_amount), 0)                        as net,
                       coalesce(sum(vat_amount), 0)                        as vat,
                       coalesce(sum(total_amount), 0)                      as total
                from documents
                where organization_id = :orgId
                  and status in ('APPROVED', 'EXPORTED')
                  and issue_date between :fromDate and :toDate
                group by to_char(issue_date, 'YYYY-MM')
                order by month
                """)
                .param("orgId", organizationId)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .query((rs, n) -> new MonthlyTotal(
                        rs.getString("month"),
                        rs.getLong("documents"),
                        rs.getBigDecimal("net"),
                        rs.getBigDecimal("vat"),
                        rs.getBigDecimal("total")))
                .list();
    }

    /**
     * Totais por categoria, ordenados por valor total descendente.
     */
    List<CategoryTotal> categoryTotals(UUID organizationId, YearMonth from, YearMonth to) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        return jdbc.sql("""
                select category,
                       count(*)::bigint                    as documents,
                       coalesce(sum(total_amount), 0)      as total
                from documents
                where organization_id = :orgId
                  and status in ('APPROVED', 'EXPORTED')
                  and issue_date between :fromDate and :toDate
                group by category
                order by total desc
                """)
                .param("orgId", organizationId)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .query((rs, n) ->
                        new CategoryTotal(rs.getString("category"), rs.getLong("documents"), rs.getBigDecimal("total")))
                .list();
    }

    /**
     * Top N fornecedores por valor total, com nome resolvido da tabela {@code suppliers}
     * quando existe (fallback para o {@code supplier_tax_id}).
     */
    List<SupplierTotal> topSuppliers(UUID organizationId, YearMonth from, YearMonth to, int limit) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        return jdbc.sql("""
                select d.supplier_tax_id                    as tax_id,
                       coalesce(s.name, d.supplier_tax_id)  as name,
                       count(*)::bigint                     as documents,
                       coalesce(sum(d.total_amount), 0)     as total
                from documents d
                left join suppliers s
                    on s.organization_id = d.organization_id
                    and s.tax_id = d.supplier_tax_id
                where d.organization_id = :orgId
                  and d.status in ('APPROVED', 'EXPORTED')
                  and d.issue_date between :fromDate and :toDate
                group by d.supplier_tax_id, s.name
                order by total desc
                limit :lim
                """)
                .param("orgId", organizationId)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("lim", limit)
                .query((rs, n) -> new SupplierTotal(
                        rs.getString("tax_id"),
                        rs.getString("name"),
                        rs.getLong("documents"),
                        rs.getBigDecimal("total")))
                .list();
    }
}
