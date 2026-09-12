package com.docgrid.document.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID id,
        String status,
        String originalFilename,
        String contentType,
        UUID duplicateOfDocumentId,
        String supplierTaxId,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal vatRate,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<ExtractedFieldResponse> fields,
        List<ValidationResultResponse> validationResults) {}
