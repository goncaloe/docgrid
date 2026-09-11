package com.docgrid.validation;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** {@code net + vat = total}, com uma tolerância de 0,02 € para arredondamentos. */
@Component
@Order(1)
class ArithmeticRule implements ValidationRule {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    @Override
    public String ruleName() {
        return "ARITHMETIC";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        BigDecimal net = context.netAmount();
        BigDecimal vat = context.vatAmount();
        BigDecimal total = context.totalAmount();
        if (net == null || vat == null || total == null) {
            return Optional.empty();
        }

        BigDecimal difference = net.add(vat).subtract(total).abs();
        boolean passed = difference.compareTo(TOLERANCE) <= 0;
        String message = passed
                ? null
                : "A base (%s) mais o IVA (%s) não bate certo com o total (%s)."
                        .formatted(net.toPlainString(), vat.toPlainString(), total.toPlainString());
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, passed, message));
    }
}
