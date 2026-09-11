package com.docgrid.validation;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Valida o dígito de controlo do NIF português (módulo 11).
 *
 * <p>Nove dígitos: os primeiros oito, pesados de 9 a 2, somados; o dígito de controlo é
 * {@code 0} se o resto da soma por 11 for menor que 2, senão {@code 11 - resto}.
 *
 * <p>Defensiva por desenho: o {@code TextractNormalizer} já normaliza o NIF na origem
 * (sem espaços, sem prefixo {@code PT}), mas esta regra não deve assumi-lo — um NIF escrito
 * à mão por um humano, numa correção futura, não passa por aquele normalizador.
 */
@Component
@Order(3)
class TaxIdRule implements ValidationRule {

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final Pattern NINE_DIGITS = Pattern.compile("[0-9]{9}");

    @Override
    public String ruleName() {
        return "TAX_ID";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        String taxId = context.supplierTaxId();
        if (taxId == null) {
            return Optional.empty();
        }

        String normalized = normalize(taxId);
        boolean valid = NINE_DIGITS.matcher(normalized).matches() && hasValidCheckDigit(normalized);
        String message = valid ? null : "O NIF do fornecedor (%s) não é válido.".formatted(taxId);
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, valid, message));
    }

    private static String normalize(String taxId) {
        String upper = taxId.trim().toUpperCase();
        String withoutPrefix = upper.startsWith("PT") ? upper.substring(2) : upper;
        return NON_DIGITS.matcher(withoutPrefix).replaceAll("");
    }

    private static boolean hasValidCheckDigit(String nineDigits) {
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            int digit = nineDigits.charAt(i) - '0';
            sum += digit * (9 - i);
        }
        int remainder = sum % 11;
        int expectedCheckDigit = remainder < 2 ? 0 : 11 - remainder;
        int actualCheckDigit = nineDigits.charAt(8) - '0';
        return expectedCheckDigit == actualCheckDigit;
    }
}
