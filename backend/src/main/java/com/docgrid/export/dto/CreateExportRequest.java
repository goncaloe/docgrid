package com.docgrid.export.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pedido de criação de uma exportação para um mês e ano.
 *
 * @param year  ano do período (ex. 2026)
 * @param month mês do período (1-12)
 */
public record CreateExportRequest(
        @Min(2020) int year, @Min(1) @Max(12) int month) {}
