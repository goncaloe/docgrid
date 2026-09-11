package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withDuplicates;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DuplicateRuleTest {

    private final DuplicateRule rule = new DuplicateRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @Test
    void passesWhenNeitherCheckFoundAnything() {
        RuleOutcome outcome = rule.evaluate(withDuplicates(clean, null, null)).orElseThrow();

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).isNull();
    }

    @Test
    void failsAndNamesTheOriginalWhenTheSameFileWasSubmittedBefore() {
        UUID original = UUID.randomUUID();

        RuleOutcome outcome =
                rule.evaluate(withDuplicates(clean, null, original)).orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains(original.toString());
    }

    @Test
    void failsAndNamesTheOriginalWhenTheSameSupplierAndInvoiceNumberWereAlreadyApproved() {
        UUID original = UUID.randomUUID();

        RuleOutcome outcome =
                rule.evaluate(withDuplicates(clean, original, null)).orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains(original.toString());
    }

    @Test
    void mentionsBothOriginalsWhenBothChecksMatch() {
        UUID sameInvoice = UUID.randomUUID();
        UUID sameFile = UUID.randomUUID();

        RuleOutcome outcome =
                rule.evaluate(withDuplicates(clean, sameInvoice, sameFile)).orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains(sameInvoice.toString()).contains(sameFile.toString());
    }
}
