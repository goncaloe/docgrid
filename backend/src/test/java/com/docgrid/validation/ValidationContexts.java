package com.docgrid.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.docgrid.document.ExtractedFieldName;

/**
 * Um {@link ValidationContext} de fatura limpa, para os testes de cada regra alterarem só
 * o que lhes interessa. Os valores de omissão passam em todas as regras — assim, um teste
 * que altera um campo sabe que qualquer falha reportada vem desse campo, não de ruído.
 */
final class ValidationContexts {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    private ValidationContexts() {}

    static ValidationContext withAmounts(ValidationContext base, BigDecimal net, BigDecimal vat, BigDecimal total) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                base.issueDate(),
                net,
                vat,
                base.vatRate(),
                total,
                base.fieldConfidences(),
                base.approvalThreshold(),
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withTaxId(ValidationContext base, String taxId) {
        return new ValidationContext(
                base.documentId(),
                taxId,
                base.invoiceNumber(),
                base.issueDate(),
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                base.fieldConfidences(),
                base.approvalThreshold(),
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withIssueDate(ValidationContext base, LocalDate issueDate) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                issueDate,
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                base.fieldConfidences(),
                base.approvalThreshold(),
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withConfidences(ValidationContext base, Map<ExtractedFieldName, BigDecimal> confidences) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                base.issueDate(),
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                confidences,
                base.approvalThreshold(),
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withDuplicates(
            ValidationContext base, UUID duplicateInvoiceDocumentId, UUID duplicateFileDocumentId) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                base.issueDate(),
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                base.fieldConfidences(),
                base.approvalThreshold(),
                duplicateInvoiceDocumentId,
                duplicateFileDocumentId,
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withApprovalThreshold(ValidationContext base, BigDecimal approvalThreshold) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                base.issueDate(),
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                base.fieldConfidences(),
                approvalThreshold,
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                base.supplierUsualCategory(),
                base.supplierOccurrenceCount(),
                base.today());
    }

    static ValidationContext withSupplierHistory(ValidationContext base, String usualCategory, int occurrenceCount) {
        return new ValidationContext(
                base.documentId(),
                base.supplierTaxId(),
                base.invoiceNumber(),
                base.issueDate(),
                base.netAmount(),
                base.vatAmount(),
                base.vatRate(),
                base.totalAmount(),
                base.fieldConfidences(),
                base.approvalThreshold(),
                base.duplicateInvoiceDocumentId(),
                base.duplicateFileDocumentId(),
                usualCategory,
                occurrenceCount,
                base.today());
    }

    static ValidationContext clean() {
        return new ValidationContext(
                UUID.randomUUID(),
                "505123452",
                "FT 2026/123",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00"),
                Map.of(
                        ExtractedFieldName.SUPPLIER_TAX_ID, new BigDecimal("0.97"),
                        ExtractedFieldName.INVOICE_NUMBER, new BigDecimal("0.95"),
                        ExtractedFieldName.ISSUE_DATE, new BigDecimal("0.98"),
                        ExtractedFieldName.NET_AMOUNT, new BigDecimal("0.96"),
                        ExtractedFieldName.VAT_AMOUNT, new BigDecimal("0.94"),
                        ExtractedFieldName.VAT_RATE, new BigDecimal("0.99"),
                        ExtractedFieldName.TOTAL_AMOUNT, new BigDecimal("0.97")),
                new BigDecimal("1000.00"),
                null,
                null,
                null,
                0,
                TODAY);
    }
}
