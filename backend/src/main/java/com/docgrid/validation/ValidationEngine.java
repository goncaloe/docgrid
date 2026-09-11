package com.docgrid.validation;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corre todas as {@link ValidationRule} conhecidas do Spring sobre um documento, grava o
 * resultado de cada uma e resume-o numa única decisão.
 *
 * <p>Revalidar reescreve: os resultados anteriores deste documento são apagados antes de
 * escrever os novos, para {@code validation_results} guardar sempre o que está errado
 * agora e não um histórico a acumular (ver o comentário na migração V3).
 */
@Service
public class ValidationEngine {

    private final List<ValidationRule> rules;
    private final ValidationResultRepository results;

    ValidationEngine(List<ValidationRule> rules, ValidationResultRepository results) {
        this.rules = rules;
        this.results = results;
    }

    @Transactional
    public ValidationSummary validate(ValidationContext context) {
        results.deleteByDocumentId(context.documentId());
        // Sem isto, o Hibernate insere os resultados novos antes de apagar os antigos (a
        // ordem de flush por omissão é inserts antes de deletes) e a constraint única em
        // (document_id, rule_name) rebenta numa revalidação.
        results.flush();

        boolean requiresReview = false;
        StringBuilder reason = new StringBuilder();

        for (ValidationRule rule : rules) {
            Optional<RuleOutcome> outcome = rule.evaluate(context);
            if (outcome.isEmpty()) {
                continue;
            }
            RuleOutcome ruleOutcome = outcome.get();
            results.save(new ValidationResult(
                    context.documentId(),
                    rule.ruleName(),
                    ruleOutcome.severity(),
                    ruleOutcome.passed(),
                    ruleOutcome.message()));

            boolean failsRule = !ruleOutcome.passed() && ruleOutcome.severity() != ValidationSeverity.INFO;
            if (failsRule) {
                requiresReview = true;
                if (!reason.isEmpty()) {
                    reason.append("; ");
                }
                reason.append(ruleOutcome.message());
            }
        }

        return new ValidationSummary(requiresReview, reason.isEmpty() ? null : reason.toString());
    }
}
