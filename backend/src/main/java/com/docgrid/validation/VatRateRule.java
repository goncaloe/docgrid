package com.docgrid.validation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** {@code vat / net} aproxima uma das taxas portuguesas: 6%, 13% ou 23%. */
@Component
@Order(2)
class VatRateRule implements ValidationRule {

    private static final List<BigDecimal> KNOWN_RATES =
            List.of(new BigDecimal("6"), new BigDecimal("13"), new BigDecimal("23"));

    /** Pontos percentuais. O produto não fixa um número; esta é a tolerância adotada. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");

    @Override
    public String ruleName() {
        return "VAT_RATE";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        BigDecimal net = context.netAmount();
        BigDecimal vat = context.vatAmount();
        if (net == null || vat == null || net.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal rate = vat.divide(net, MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        boolean matchesKnownRate = KNOWN_RATES.stream()
                .anyMatch(known -> rate.subtract(known).abs().compareTo(TOLERANCE) <= 0);
        String message = matchesKnownRate
                ? null
                : "A taxa de IVA calculada (%s%%) não aproxima 6%%, 13%% ou 23%%.".formatted(rate.toPlainString());
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, matchesKnownRate, message));
    }
}
