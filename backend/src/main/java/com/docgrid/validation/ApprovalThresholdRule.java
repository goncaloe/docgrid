package com.docgrid.validation;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nunca manda para revisão: só assinala, para quem revê, que o total exige aprovação de
 * um gestor. Informativo — "exige aprovação de gestor" não é o mesmo que "algo está
 * errado".
 */
@Component
@Order(7)
class ApprovalThresholdRule implements ValidationRule {

    @Override
    public String ruleName() {
        return "APPROVAL_THRESHOLD";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        BigDecimal total = context.totalAmount();
        BigDecimal threshold = context.approvalThreshold();
        if (total == null || threshold == null || total.compareTo(threshold) <= 0) {
            return Optional.empty();
        }

        String message = "O total (%s) excede o limite de aprovação da organização (%s): exige aprovação de gestor."
                .formatted(total.toPlainString(), threshold.toPlainString());
        return Optional.of(new RuleOutcome(ValidationSeverity.INFO, true, message));
    }
}
