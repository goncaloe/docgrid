package com.docgrid.export.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de criação ou listagem de uma exportação.
 *
 * @param id                identificador único da exportação
 * @param year              ano do período exportado
 * @param month             mês do período exportado (1-12)
 * @param documentCount     número de documentos incluídos
 * @param netTotal          soma do valor líquido
 * @param vatTotal          soma do IVA
 * @param total             soma do valor total
 * @param createdAt         instante de criação
 * @param documentsWithoutDate número de documentos aprovados sem issue_date que ficaram
 *                             de fora (não têm data para ser incluídos em nenhum período)
 */
public record ExportResponse(
        UUID id,
        int year,
        int month,
        long documentCount,
        BigDecimal netTotal,
        BigDecimal vatTotal,
        BigDecimal total,
        Instant createdAt,
        long documentsWithoutDate) {}
