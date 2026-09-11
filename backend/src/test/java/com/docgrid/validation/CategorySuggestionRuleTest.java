package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withSupplierHistory;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategorySuggestionRuleTest {

    private final CategorySuggestionRule rule = new CategorySuggestionRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @Test
    void suggestsTheCategoryAfterFiveConsecutiveApprovals() {
        RuleOutcome outcome =
                rule.evaluate(withSupplierHistory(clean, "Combustível", 5)).orElseThrow();

        assertThat(outcome.severity()).isEqualTo(ValidationSeverity.INFO);
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).contains("Combustível").contains("5");
    }

    @Test
    void abstainsBeforeFiveOccurrences() {
        assertThat(rule.evaluate(withSupplierHistory(clean, "Combustível", 4))).isEmpty();
    }

    @Test
    void abstainsWhenThereIsNoUsualCategoryYet() {
        assertThat(rule.evaluate(withSupplierHistory(clean, null, 10))).isEmpty();
    }
}
