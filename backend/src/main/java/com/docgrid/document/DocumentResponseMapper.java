package com.docgrid.document;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import com.docgrid.document.dto.DocumentDetailResponse;
import com.docgrid.document.dto.DocumentSummaryResponse;
import com.docgrid.document.dto.ExtractedFieldResponse;
import com.docgrid.document.dto.PolygonPointResponse;
import com.docgrid.document.dto.ValidationResultResponse;
import com.docgrid.validation.ValidationResultProvider;
import com.docgrid.validation.ValidationResultView;

/** Traduz as entidades do pacote (nunca expostas) para os DTOs que a API devolve. */
@Component
class DocumentResponseMapper {

    private final ValidationResultProvider validationResults;
    private final DocumentValidationContextFactory validationContexts;
    private final ObjectMapper objectMapper;

    DocumentResponseMapper(
            ValidationResultProvider validationResults,
            DocumentValidationContextFactory validationContexts,
            ObjectMapper objectMapper) {
        this.validationResults = validationResults;
        this.validationContexts = validationContexts;
        this.objectMapper = objectMapper;
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
                        field.getSource().name(),
                        field.getPage(),
                        toPolygon(field.getBoundingBox())))
                .toList();
        List<ValidationResultResponse> results = validationResults.resultsFor(document.getId()).stream()
                .map(this::toValidationResult)
                .toList();
        return new DocumentDetailResponse(
                document.getId(),
                document.getStatus().name(),
                document.getOriginalFilename(),
                document.getContentType(),
                validationContexts.duplicateOf(document),
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

    /**
     * O polígono guardado como string JSON {@code [[x,y],...]} ({@link ExtractedField#getBoundingBox()}),
     * convertido para a lista de pontos que a API expõe. {@code null} quando o campo não tem geometria
     * (origem {@code HUMAN}).
     */
    private List<PolygonPointResponse> toPolygon(String boundingBox) {
        if (boundingBox == null) {
            return null;
        }
        try {
            double[][] points = objectMapper.readValue(boundingBox, double[][].class);
            return Arrays.stream(points)
                    .map(point -> new PolygonPointResponse(point[0], point[1]))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Polígono guardado em formato inesperado: " + boundingBox, e);
        }
    }
}
