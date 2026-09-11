package com.docgrid.validation;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A data de emissão está entre 24 meses atrás e hoje mais 7 dias. */
@Component
@Order(6)
class PlausibleDateRule implements ValidationRule {

    @Override
    public String ruleName() {
        return "ISSUE_DATE";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        LocalDate issueDate = context.issueDate();
        if (issueDate == null) {
            return Optional.empty();
        }

        LocalDate earliest = context.today().minusMonths(24);
        LocalDate latest = context.today().plusDays(7);
        boolean plausible = !issueDate.isBefore(earliest) && !issueDate.isAfter(latest);
        String message = plausible
                ? null
                : "A data de emissão (%s) está fora do intervalo plausível (%s a %s)."
                        .formatted(issueDate, earliest, latest);
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, plausible, message));
    }
}
