package com.docgrid.validation;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
class JpaValidationResultProvider implements ValidationResultProvider {

    private final ValidationResultRepository results;

    JpaValidationResultProvider(ValidationResultRepository results) {
        this.results = results;
    }

    @Override
    public List<ValidationResultView> resultsFor(UUID documentId) {
        return results.findByDocumentId(documentId).stream()
                .map(result -> new ValidationResultView(
                        result.getRuleName(), result.getSeverity(), result.hasPassed(), result.getMessage()))
                .toList();
    }
}
