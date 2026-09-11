package com.docgrid.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.validation.ValidationEngine;
import com.docgrid.validation.ValidationSummary;

/**
 * Corrige um campo à mão: grava o valor novo com origem {@code HUMAN} (regra 6 do
 * {@code AGENTS.md}), guarda o valor anterior no histórico, atualiza a projeção de negócio
 * do documento e revalida.
 *
 * <p>Revalidar não muda o estado sozinho para {@code EXTRACTED} — o ciclo de vida não tem
 * essa transição a partir de {@code NEEDS_REVIEW} (só {@code APPROVED} ou {@code REJECTED},
 * ver {@code docs/01-PRODUCT.md}): quem revê corrige e aprova, não espera que o sistema
 * mude o estado por ela. Só transita quando a transição de facto existe — tipicamente
 * {@code EXTRACTED → NEEDS_REVIEW}, se a correção piorar alguma coisa.
 */
@Service
public class FieldCorrectionService {

    private final DocumentRepository documents;
    private final ExtractedFieldRepository fields;
    private final DocumentEventRepository events;
    private final ValidationEngine validationEngine;
    private final DocumentValidationContextFactory validationContexts;

    FieldCorrectionService(
            DocumentRepository documents,
            ExtractedFieldRepository fields,
            DocumentEventRepository events,
            ValidationEngine validationEngine,
            DocumentValidationContextFactory validationContexts) {
        this.documents = documents;
        this.fields = fields;
        this.events = events;
        this.validationEngine = validationEngine;
        this.validationContexts = validationContexts;
    }

    /**
     * @throws DocumentNotFoundException se o documento não existir nesta organização
     * @throws DocumentNotEditableException se o documento não estiver em {@code EXTRACTED}
     *     nem {@code NEEDS_REVIEW}
     * @throws InvalidFieldValueException se o valor não tiver o formato que o campo espera
     */
    @Transactional
    public void correct(
            UUID documentId, UUID organizationId, ExtractedFieldName fieldName, String newValue, Actor actor) {
        Document document = documents
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (document.getStatus() != DocumentStatus.EXTRACTED && document.getStatus() != DocumentStatus.NEEDS_REVIEW) {
            throw new DocumentNotEditableException(document.getStatus());
        }

        ExtractedField field =
                fields.findByDocumentIdAndFieldName(documentId, fieldName).orElse(null);
        String oldValue = field == null ? null : field.getValueText();
        if (field == null) {
            ExtractedField.writtenByHuman(document, fieldName, newValue);
        } else {
            field.correctTo(newValue);
        }
        events.save(DocumentEvent.fieldCorrected(documentId, fieldName, oldValue, newValue, actor));

        document.projectInvoiceFields(projectFromCurrentFields(document));

        ValidationSummary summary = validationEngine.validate(
                validationContexts.build(document, DocumentValidationContextFactory.currentFieldConfidences(document)));
        DocumentStatus target = summary.requiresReview() ? DocumentStatus.NEEDS_REVIEW : DocumentStatus.EXTRACTED;
        if (target != document.getStatus() && document.getStatus().canTransitionTo(target)) {
            events.save(document.transitionTo(target, actor, summary.reason()));
        }
    }

    /** Reconstrói a projeção de negócio a partir dos campos tal como estão guardados agora. */
    private InvoiceFields projectFromCurrentFields(Document document) {
        Map<ExtractedFieldName, String> values = new EnumMap<>(ExtractedFieldName.class);
        for (ExtractedField field : document.getExtractedFields()) {
            values.put(field.getFieldName(), field.getValueText());
        }
        return new InvoiceFields(
                values.get(ExtractedFieldName.SUPPLIER_NAME),
                values.get(ExtractedFieldName.SUPPLIER_TAX_ID),
                values.get(ExtractedFieldName.INVOICE_NUMBER),
                parseDate(ExtractedFieldName.ISSUE_DATE, values.get(ExtractedFieldName.ISSUE_DATE)),
                parseAmount(ExtractedFieldName.NET_AMOUNT, values.get(ExtractedFieldName.NET_AMOUNT)),
                parseAmount(ExtractedFieldName.VAT_AMOUNT, values.get(ExtractedFieldName.VAT_AMOUNT)),
                parseAmount(ExtractedFieldName.VAT_RATE, values.get(ExtractedFieldName.VAT_RATE)),
                parseAmount(ExtractedFieldName.TOTAL_AMOUNT, values.get(ExtractedFieldName.TOTAL_AMOUNT)));
    }

    private static LocalDate parseDate(ExtractedFieldName fieldName, String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidFieldValueException(fieldName, value);
        }
    }

    private static BigDecimal parseAmount(ExtractedFieldName fieldName, String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidFieldValueException(fieldName, value);
        }
    }
}
