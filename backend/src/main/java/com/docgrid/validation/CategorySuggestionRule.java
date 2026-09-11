package com.docgrid.validation;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sugere a categoria habitual do fornecedor quando o histórico já a sustenta — cinco ou
 * mais aprovações seguidas na mesma categoria. Nunca bloqueia: é sugestão, não imposição.
 */
@Component
@Order(8)
class CategorySuggestionRule implements ValidationRule {

    private static final int MINIMUM_OCCURRENCES = 5;

    @Override
    public String ruleName() {
        return "CATEGORY_SUGGESTION";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        String category = context.supplierUsualCategory();
        int occurrences = context.supplierOccurrenceCount();
        if (category == null || occurrences < MINIMUM_OCCURRENCES) {
            return Optional.empty();
        }

        String message = "Categoria sugerida: %s (histórico de %d documentos aprovados deste fornecedor)."
                .formatted(category, occurrences);
        return Optional.of(new RuleOutcome(ValidationSeverity.INFO, true, message));
    }
}
