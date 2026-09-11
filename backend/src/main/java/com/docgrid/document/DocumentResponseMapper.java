package com.docgrid.document;

import java.util.List;

import org.springframework.stereotype.Component;

import com.docgrid.document.dto.DocumentDetailResponse;
import com.docgrid.document.dto.DocumentSummaryResponse;
import com.docgrid.document.dto.ExtractedFieldResponse;
import com.docgrid.document.dto.ValidationResultResponse;
import com.docgrid.validation.ValidationResultProvider;
import com.docgrid.validation.ValidationResultView;

/** Traduz as entidades do pacote (nunca expostas) para os DTOs que a API devolve. */
@Component
class DocumentResponseMapper {

    private final ValidationResultProvider validationResults;

    DocumentResponseMapper(ValidationResultProvider validationResults) {
        this.validationResults = validationResults;
    }

    DocumentSummaryResponse toSummary(Document document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getStatus().name(),
                document.getSupplierTaxId(),
                document.getInvoiceNumber(),
                document.getIssueDate(),
                document.getTotalAmount(),
                document.getCurrency(),
                document.getCreatedAt());
    }

    DocumentDetailResponse toDetail(Document document) {
        List<ExtractedFieldResponse> fields = document.getExtractedFields().stream()
                .map(field -> new ExtractedFieldResponse(
                        field.getFieldName().name(),
                        field.getValueText(),
                        field.getConfidence(),
                        field.getSource().name()))
                .toList();
        List<ValidationResultResponse> results = validationResults.resultsFor(document.getId()).stream()
                .map(this::toValidationResult)
                .toList();
        return new DocumentDetailResponse(
                document.getId(),
                document.getStatus().name(),
                document.getOriginalFilename(),
                document.getSupplierTaxId(),
                document.getInvoiceNumber(),
                document.getIssueDate(),
                document.getNetAmount(),
                document.getVatAmount(),
                document.getVatRate(),
                document.getTotalAmount(),
                document.getCurrency(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                fields,
                results);
    }

    private ValidationResultResponse toValidationResult(ValidationResultView view) {
        return new ValidationResultResponse(view.ruleName(), view.severity().name(), view.passed(), view.message());
    }
}
