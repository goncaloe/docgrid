package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withConfidences;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.docgrid.document.ExtractedFieldName;

class MinConfidenceRuleTest {

    private final MinConfidenceRule rule = new MinConfidenceRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @Test
    void passesWhenAllSevenFiscalFieldsAreAboveTheThreshold() {
        RuleOutcome outcome = rule.evaluate(clean).orElseThrow();

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).isNull();
    }

    @Test
    void failsWhenOneFieldIsBelowTheThreshold() {
        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(clean.fieldConfidences());
        confidences.put(ExtractedFieldName.NET_AMOUNT, new BigDecimal("0.50"));

        RuleOutcome outcome = rule.evaluate(withConfidences(clean, confidences)).orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("NET_AMOUNT");
    }

    @Test
    void treatsAMissingRequiredFieldAsAFailure() {
        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(clean.fieldConfidences());
        confidences.remove(ExtractedFieldName.INVOICE_NUMBER);

        RuleOutcome outcome = rule.evaluate(withConfidences(clean, confidences)).orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("INVOICE_NUMBER");
    }

    @Test
    void ignoresFieldsThatAreNotFiscallyRequired() {
        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(clean.fieldConfidences());
        confidences.put(ExtractedFieldName.SUPPLIER_NAME, new BigDecimal("0.10"));

        RuleOutcome outcome = rule.evaluate(withConfidences(clean, confidences)).orElseThrow();

        assertThat(outcome.passed()).isTrue();
    }
}
