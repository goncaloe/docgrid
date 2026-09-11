package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withAmounts;
import static com.docgrid.validation.ValidationContexts.withApprovalThreshold;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ApprovalThresholdRuleTest {

    private final ApprovalThresholdRule rule = new ApprovalThresholdRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @Test
    void abstainsWhenTheTotalIsAtOrBelowTheThreshold() {
        ValidationContext context = withApprovalThreshold(clean, clean.totalAmount());
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void neverBlocksButFlagsWhenTheTotalExceedsTheThreshold() {
        ValidationContext context = withApprovalThreshold(clean, new BigDecimal("100.00"));

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.severity()).isEqualTo(ValidationSeverity.INFO);
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).contains("aprovação de gestor");
    }

    @Test
    void abstainsWhenDataIsMissing() {
        assertThat(rule.evaluate(withAmounts(clean, clean.netAmount(), clean.vatAmount(), null)))
                .isEmpty();
        assertThat(rule.evaluate(withApprovalThreshold(clean, null))).isEmpty();
    }
}
