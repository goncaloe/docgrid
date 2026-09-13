package com.docgrid.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta completa do dashboard, com os cinco blocos de agregação que o frontend
 * consome.
 *
 * @param totals       totais do período (documentos, líquido, IVA, total)
 * @param operations   indicadores operacionais (documentos processados, taxa de
 *                     automação, tempo médio de revisão)
 * @param monthly      série mensal dos totais
 * @param categories   agregação por categoria
 * @param topSuppliers top N fornecedores por valor total
 */
public record DashboardResponse(
        PeriodTotals totals,
        Operations operations,
        List<MonthlyTotal> monthly,
        List<CategoryTotal> categories,
        List<SupplierTotal> topSuppliers) {

    /**
     * Totais do período filtrado.
     *
     * @param documents número de documentos aprovados/exportados no período
     * @param net       soma dos valores líquidos
     * @param vat       soma dos valores de IVA
     * @param total     soma dos valores totais
     */
    public record PeriodTotals(long documents, BigDecimal net, BigDecimal vat, BigDecimal total) {}

    /**
     * Indicadores operacionais do período.
     *
     * @param processedDocuments    documentos cujo estado já saiu de
     *                              {@code UPLOADED/PROCESSING/FAILED}
     * @param automationRate        fração (0–1) de documentos que nunca passaram por
     *                              revisão humana; {@code null} quando não há documentos
     *                              processados
     * @param averageReviewSeconds  tempo médio em segundos entre a primeira passagem a
     *                              {@code NEEDS_REVIEW} e a decisão ({@code APPROVED} ou
     *                              {@code REJECTED}); {@code null} quando não há ciclos
     *                              de revisão concluídos
     */
    public record Operations(long processedDocuments, BigDecimal automationRate, Long averageReviewSeconds) {}

    /**
     * Totais agrupados por mês-base ({@code issue_date} do documento).
     *
     * @param month     mês no formato {@code "yyyy-MM"}
     * @param documents número de documentos no mês
     * @param net       soma dos valores líquidos
     * @param vat       soma dos valores de IVA
     * @param total     soma dos valores totais
     */
    public record MonthlyTotal(String month, long documents, BigDecimal net, BigDecimal vat, BigDecimal total) {}

    /**
     * Totais agrupados por categoria.
     *
     * @param category  nome da categoria; {@code null} significa "sem categoria"
     * @param documents número de documentos
     * @param total     soma dos valores totais
     */
    public record CategoryTotal(String category, long documents, BigDecimal total) {}

    /**
     * Fornecedores ordenados por valor total descendente (top N).
     *
     * @param taxId     NIF do fornecedor
     * @param name      nome do fornecedor (vindo da tabela {@code suppliers} quando
     *                  existe, ou do próprio {@code taxId} como fallback)
     * @param documents número de documentos
     * @param total     soma dos valores totais
     */
    public record SupplierTotal(String taxId, String name, long documents, BigDecimal total) {}
}
