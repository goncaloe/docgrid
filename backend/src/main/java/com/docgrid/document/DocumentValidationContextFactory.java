package com.docgrid.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.docgrid.auth.ApprovalThresholdProvider;
import com.docgrid.supplier.SupplierHistory;
import com.docgrid.supplier.SupplierHistoryProvider;
import com.docgrid.validation.ValidationContext;

/**
 * Monta o que o motor de validação precisa: as consultas de duplicado já resolvidas, o
 * limite de aprovação da organização, o histórico do fornecedor. O motor não conhece
 * {@link DocumentRepository}, só interpreta o que já foi encontrado.
 *
 * <p>Reutilizada pela extração inicial ({@link DocumentProcessor}, com a confiança que a
 * máquina acabou de ler) e pela correção manual de campo ({@code FieldCorrectionService},
 * com a confiança dos campos tal como estão guardados agora).
 */
@Component
class DocumentValidationContextFactory {

    private final DocumentRepository documents;
    private final ApprovalThresholdProvider approvalThresholds;
    private final SupplierHistoryProvider supplierHistories;

    DocumentValidationContextFactory(
            DocumentRepository documents,
            ApprovalThresholdProvider approvalThresholds,
            SupplierHistoryProvider supplierHistories) {
        this.documents = documents;
        this.approvalThresholds = approvalThresholds;
        this.supplierHistories = supplierHistories;
    }

    /**
     * @param fieldConfidences a confiança de cada campo, no formato que o motor espera; ver
     *     {@link #currentFieldConfidences(Document)} para o caso de revalidação
     */
    ValidationContext build(Document document, Map<ExtractedFieldName, BigDecimal> fieldConfidences) {
        UUID duplicateInvoiceDocumentId = findDuplicateInvoiceDocumentId(document);
        UUID duplicateFileDocumentId = findDuplicateFileDocumentId(document);
        SupplierHistory supplierHistory =
                supplierHistories.historyFor(document.getOrganizationId(), document.getSupplierTaxId());

        return new ValidationContext(
                document.getId(),
                document.getSupplierTaxId(),
                document.getInvoiceNumber(),
                document.getIssueDate(),
                document.getNetAmount(),
                document.getVatAmount(),
                document.getVatRate(),
                document.getTotalAmount(),
                fieldConfidences,
                approvalThresholds.approvalThresholdFor(document.getOrganizationId()),
                duplicateInvoiceDocumentId,
                duplicateFileDocumentId,
                supplierHistory.usualCategory(),
                supplierHistory.occurrenceCount(),
                LocalDate.now());
    }

    /**
     * A confiança de cada campo tal como está guardado agora: a da máquina para um campo
     * {@code AI}, plena (1) para um campo corrigido à mão — a origem {@code HUMAN} já diz
     * que uma pessoa o confirmou, não há incerteza a declarar sobre ele.
     */
    static Map<ExtractedFieldName, BigDecimal> currentFieldConfidences(Document document) {
        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(ExtractedFieldName.class);
        for (ExtractedField field : document.getExtractedFields()) {
            confidences.put(
                    field.getFieldName(),
                    field.getSource() == FieldSource.HUMAN ? BigDecimal.ONE : field.getConfidence());
        }
        return confidences;
    }

    /** A mesma fatura (NIF+número) já aprovada — só isso conta como duplicado de negócio. */
    private UUID findDuplicateInvoiceDocumentId(Document document) {
        if (document.getSupplierTaxId() == null || document.getInvoiceNumber() == null) {
            return null;
        }
        return documents
                .findByOrganizationIdAndSupplierTaxIdAndInvoiceNumber(
                        document.getOrganizationId(), document.getSupplierTaxId(), document.getInvoiceNumber())
                .stream()
                .filter(other -> !other.getId().equals(document.getId()))
                .filter(other -> other.getStatus() == DocumentStatus.APPROVED)
                .min(Comparator.comparing(Document::getCreatedAt))
                .map(Document::getId)
                .orElse(null);
    }

    /**
     * O mesmo ficheiro submetido outra vez. Exclui {@code REJECTED}: depois de uma rejeição,
     * reenviar o mesmo PDF não deve ficar preso num falso duplicado eterno.
     */
    private UUID findDuplicateFileDocumentId(Document document) {
        return documents.findByOrganizationIdAndFileHash(document.getOrganizationId(), document.getFileHash()).stream()
                .filter(other -> !other.getId().equals(document.getId()))
                .filter(other -> other.getStatus() != DocumentStatus.REJECTED)
                .min(Comparator.comparing(Document::getCreatedAt))
                .map(Document::getId)
                .orElse(null);
    }
}
