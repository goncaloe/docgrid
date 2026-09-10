package com.docgrid.document;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os campos de negócio de uma fatura, no formato em que se guardam.
 *
 * <p>Existe para haver uma só assinatura a escrever a projeção em {@link Document}:
 * argumentos soltos convidam a trocar dois deles sem o compilador dar por isso. Quem o
 * constrói é o motor de extração ({@code com.docgrid.extraction}).
 *
 * <p>O {@code supplierName} não tem coluna na projeção de {@code documents} (ADR 0003) —
 * não serve para procurar nem agregar; vive só em {@code extracted_fields}, com a sua
 * confiança e o seu bounding box.
 */
public record InvoiceFields(
        String supplierName,
        String supplierTaxId,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal vatRate,
        BigDecimal totalAmount) {}
