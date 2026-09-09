package com.docgrid.document;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os campos de negócio de uma fatura, no formato em que se guardam.
 *
 * <p>Existe para haver uma só assinatura a escrever a projeção em {@link Document}: sete
 * argumentos soltos convidam a trocar dois deles sem o compilador dar por isso. Quem o
 * constrói é o motor de extração ({@code com.docgrid.extraction}).
 */
public record InvoiceFields(
        String supplierTaxId,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal vatRate,
        BigDecimal totalAmount) {}
