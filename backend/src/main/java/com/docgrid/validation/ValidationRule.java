package com.docgrid.validation;

import java.util.Optional;

/**
 * Uma regra de negócio, testável isoladamente e descoberta pelo Spring.
 *
 * <p>Acrescentar uma regra nova é criar uma classe {@code @Component} que implemente esta
 * interface — o {@link ValidationEngine} injeta a lista inteira e não precisa de saber
 * quantas há nem o que cada uma faz.
 */
public interface ValidationRule {

    /** Identifica a linha em {@code validation_results}. Estável: não renomear à toa. */
    String ruleName();

    /**
     * Avalia o documento, ou abstém-se.
     *
     * @return vazio se a regra não tem dados suficientes para se pronunciar — nesse caso
     *     não se escreve nenhuma linha para esta regra. Um dado de negócio em falta já é
     *     apanhado por {@code MinConfidenceRule}; esta regra não deve inventar um segundo
     *     aviso para a mesma causa.
     */
    Optional<RuleOutcome> evaluate(ValidationContext context);
}
