package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withIssueDate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PlausibleDateRuleTest {

    private final PlausibleDateRule rule = new PlausibleDateRule();
    private final ValidationContext clean = ValidationContexts.clean();
    private static final LocalDate TODAY = ValidationContexts.TODAY;

    @Test
    void passesForADateWellWithinTheWindow() {
        assertThat(rule.evaluate(withIssueDate(clean, TODAY.minusMonths(1)))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    @Test
    void passesAtTheExactEdgesOfTheWindow() {
        assertThat(rule.evaluate(withIssueDate(clean, TODAY.minusMonths(24)))
                        .orElseThrow()
                        .passed())
                .isTrue();
        assertThat(rule.evaluate(withIssueDate(clean, TODAY.plusDays(7)))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    @Test
    void failsForADateMoreThanTwentyFourMonthsInThePast() {
        RuleOutcome outcome = rule.evaluate(
                        withIssueDate(clean, TODAY.minusMonths(24).minusDays(1)))
                .orElseThrow();

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).isNotNull();
    }

    @Test
    void failsForADateMoreThanSevenDaysInTheFuture() {
        assertThat(rule.evaluate(withIssueDate(clean, TODAY.plusDays(8)))
                        .orElseThrow()
                        .passed())
                .isFalse();
    }

    @Test
    void abstainsWhenThereIsNoIssueDate() {
        assertThat(rule.evaluate(withIssueDate(clean, null))).isEmpty();
    }
}
