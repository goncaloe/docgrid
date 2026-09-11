package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withAmounts;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VatRateRuleTest {

    private final VatRateRule rule = new VatRateRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @ParameterizedTest(name = "{0} de IVA sobre {1} de base passa")
    @MethodSource("knownRates")
    void passesForEachKnownPortugueseRate(BigDecimal vat, BigDecimal net) {
        assertThat(rule.evaluate(withAmounts(clean, net, vat, net.add(vat)))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    static Stream<Arguments> knownRates() {
        return Stream.of(
                Arguments.of(new BigDecimal("6.00"), new BigDecimal("100.00")),
                Arguments.of(new BigDecimal("13.00"), new BigDecimal("100.00")),
                Arguments.of(new BigDecimal("23.00"), new BigDecimal("100.00")));
    }

    @Test
    void failsForARateThatMatchesNoKnownBracket() {
        Optional<RuleOutcome> outcome = withRate(new BigDecimal("100.00"), new BigDecimal("19.40"));

        assertThat(outcome).hasValueSatisfying(o -> {
            assertThat(o.passed()).isFalse();
            assertThat(o.message()).contains("19.40").doesNotContain("null");
        });
    }

    @Test
    void abstainsWhenNetIsMissingOrZero() {
        assertThat(rule.evaluate(withAmounts(clean, null, new BigDecimal("23.00"), null)))
                .isEmpty();
        assertThat(rule.evaluate(withAmounts(clean, BigDecimal.ZERO, new BigDecimal("23.00"), null)))
                .isEmpty();
    }

    private Optional<RuleOutcome> withRate(BigDecimal net, BigDecimal vat) {
        return rule.evaluate(withAmounts(clean, net, vat, net.add(vat)));
    }
}
