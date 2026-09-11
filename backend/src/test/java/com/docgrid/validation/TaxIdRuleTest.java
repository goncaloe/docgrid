package com.docgrid.validation;

import static com.docgrid.validation.ValidationContexts.withTaxId;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * O dígito de controlo módulo 11 do NIF português. Pelo menos dez casos, como o critério
 * de aceitação da etapa 05 pede: válidos, inválidos, comprimento errado, não numérico, com
 * espaços, com prefixo.
 */
class TaxIdRuleTest {

    private final TaxIdRule rule = new TaxIdRule();
    private final ValidationContext clean = ValidationContexts.clean();

    @ParameterizedTest(name = "\"{0}\" -> válido={1}")
    @MethodSource("cases")
    void validatesTheCheckDigit(String taxId, boolean expectedValid) {
        RuleOutcome outcome = rule.evaluate(withTaxId(clean, taxId)).orElseThrow();

        assertThat(outcome.passed()).isEqualTo(expectedValid);
        assertThat(outcome.severity()).isEqualTo(ValidationSeverity.WARNING);
        if (!expectedValid) {
            assertThat(outcome.message()).contains(taxId);
        } else {
            assertThat(outcome.message()).isNull();
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("505123452", true), // a fixture clean-invoice.json
                Arguments.of("123456789", true),
                Arguments.of("987654322", true),
                Arguments.of("111111110", true),
                Arguments.of("505123451", false), // dígito de controlo errado
                Arguments.of("505123450", false), // dígito de controlo errado
                Arguments.of("123456780", false), // dígito de controlo errado
                Arguments.of("50512345", false), // 8 dígitos, curto demais
                Arguments.of("5051234522", false), // 10 dígitos, longo demais
                Arguments.of("50512345A", false), // não numérico
                Arguments.of(" 505123452 ", true), // espaços a mais, normalizados
                Arguments.of("PT505123452", true), // com prefixo
                Arguments.of("pt 505 123 452", true), // prefixo minúsculo e espaços internos
                Arguments.of("", false));
    }

    @Test
    void abstainsWhenThereIsNoTaxIdAtAll() {
        assertThat(rule.evaluate(withTaxId(clean, null))).isEmpty();
    }
}
