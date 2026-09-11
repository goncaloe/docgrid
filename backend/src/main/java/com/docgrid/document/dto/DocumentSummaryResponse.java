package com.docgrid.document.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Uma linha da listagem ou de uma fila — sem os campos extraídos nem o histórico. */
public record DocumentSummaryResponse(
        UUID id,
        String status,
        String supplierTaxId,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt) {}
