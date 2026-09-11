package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withAmounts;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ArithmeticRuleTest {

    private final ArithmeticRule rule = new ArithmeticRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @Test
    void passesWhenNetPlusVatEqualsTotal() {
        Optional<RuleOutcome> outcome = rule.evaluate(
                withAmounts(clean, new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00")));

        assertThat(outcome).hasValueSatisfying(o -> {
            assertThat(o.passed()).isTrue();
            assertThat(o.message()).isNull();
        });
    }

    @Test
    void toleratesTwoCentsOfRoundingEitherWay() {
        assertThat(rule.evaluate(withAmounts(
                                clean, new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.02")))
                        .orElseThrow()
                        .passed())
                .isTrue();
        assertThat(rule.evaluate(withAmounts(
                                clean, new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("122.98")))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    @Test
    void failsWhenTheDifferenceExceedsTheTolerance() {
        Optional<RuleOutcome> outcome = rule.evaluate(
                withAmounts(clean, new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("150.00")));

        assertThat(outcome).hasValueSatisfying(o -> {
            assertThat(o.passed()).isFalse();
            assertThat(o.severity()).isEqualTo(ValidationSeverity.WARNING);
            assertThat(o.message()).contains("100.00").contains("23.00").contains("150.00");
        });
    }

    @Test
    void abstainsWhenAnyAmountIsMissing() {
        assertThat(rule.evaluate(withAmounts(clean, null, new BigDecimal("23.00"), new BigDecimal("123.00"))))
                .isEmpty();
        assertThat(rule.evaluate(withAmounts(clean, new BigDecimal("100.00"), null, new BigDecimal("123.00"))))
                .isEmpty();
        assertThat(rule.evaluate(withAmounts(clean, new BigDecimal("100.00"), new BigDecimal("23.00"), null)))
                .isEmpty();
    }
}
