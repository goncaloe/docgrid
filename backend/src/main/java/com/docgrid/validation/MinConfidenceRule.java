package com.docgrid.validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.docgrid.document.ExtractedFieldName;

/**
 * Todos os campos fiscais obrigatórios acima de 0.85 de confiança. Um campo obrigatório
 * ausente do mapa (a extração não o conseguiu ler) conta como falha, não como "sem dados"
 * — é precisamente o que esta regra existe para apanhar.
 */
@Component
@Order(5)
class MinConfidenceRule implements ValidationRule {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.85");

    /**
     * Os campos que entram nas outras regras de negócio e na exportação contabilística.
     * {@code SUPPLIER_NAME}, {@code CURRENCY} e {@code CATEGORY} ficam de fora: informativo,
     * suposto por omissão, e sugestão, respetivamente.
     */
    private static final List<ExtractedFieldName> REQUIRED_FIELDS = List.of(
            ExtractedFieldName.SUPPLIER_TAX_ID,
            ExtractedFieldName.INVOICE_NUMBER,
            ExtractedFieldName.ISSUE_DATE,
            ExtractedFieldName.NET_AMOUNT,
            ExtractedFieldName.VAT_AMOUNT,
            ExtractedFieldName.VAT_RATE,
            ExtractedFieldName.TOTAL_AMOUNT);

    @Override
    public String ruleName() {
        return "MIN_CONFIDENCE";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        List<String> problems = REQUIRED_FIELDS.stream()
                .filter(field -> belowThresholdOrMissing(context, field))
                .map(Enum::name)
                .collect(Collectors.toList());

        boolean passed = problems.isEmpty();
        String message = passed ? null : "Confiança insuficiente em: %s.".formatted(String.join(", ", problems));
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, passed, message));
    }

    private static boolean belowThresholdOrMissing(ValidationContext context, ExtractedFieldName field) {
        BigDecimal confidence = context.fieldConfidences().get(field);
        return confidence == null || confidence.compareTo(THRESHOLD) < 0;
    }
}
