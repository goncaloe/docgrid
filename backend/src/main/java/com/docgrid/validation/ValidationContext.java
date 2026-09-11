package com.docgrid.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.docgrid.document.ExtractedFieldName;

/**
 * Tudo o que as regras precisam para se pronunciar sobre um documento, pré-calculado por
 * quem chama o motor. As regras não conhecem JPA nem outros pacotes — é assim que se
 * testam isoladamente, sem levantar o Spring.
 *
 * <p>Sem {@link java.util.Optional} nos campos, por convenção do projeto: um dado ausente
 * é {@code null} (ou, para {@code supplierOccurrenceCount}, zero).
 *
 * @param fieldConfidences confiança de cada campo lido pela máquina; um campo ausente do
 *     mapa é um campo que a extração não conseguiu ler
 * @param duplicateInvoiceDocumentId id do documento já {@code APPROVED} com o mesmo NIF e
 *     número de fatura, ou {@code null} se não houver
 * @param duplicateFileDocumentId id de outro documento (não {@code REJECTED}) com o mesmo
 *     hash de ficheiro, ou {@code null} se não houver
 * @param today a data de hoje, passada explicitamente para a regra de data plausível ser
 *     pura e testável sem depender do relógio do sistema
 */
public record ValidationContext(
        UUID documentId,
        String supplierTaxId,
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal vatRate,
        BigDecimal totalAmount,
        Map<ExtractedFieldName, BigDecimal> fieldConfidences,
        BigDecimal approvalThreshold,
        UUID duplicateInvoiceDocumentId,
        UUID duplicateFileDocumentId,
        String supplierUsualCategory,
        int supplierOccurrenceCount,
        LocalDate today) {}
