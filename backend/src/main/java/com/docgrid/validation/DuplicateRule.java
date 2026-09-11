package com.docgrid.validation;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A mesma fatura outra vez, por dois critérios independentes: o mesmo ficheiro submetido
 * de novo (hash binário) e o mesmo NIF+número de fatura já aprovado. Os dois são
 * verificados por quem chama o motor — esta regra não conhece {@code DocumentRepository},
 * só interpreta o que já foi encontrado.
 *
 * <p>Uma única linha em {@code validation_results} cobre os dois critérios: são a mesma
 * pergunta de negócio ("já processámos isto?"), só que respondida de duas formas.
 */
@Component
@Order(4)
class DuplicateRule implements ValidationRule {

    @Override
    public String ruleName() {
        return "DUPLICATE";
    }

    @Override
    public Optional<RuleOutcome> evaluate(ValidationContext context) {
        boolean sameFile = context.duplicateFileDocumentId() != null;
        boolean sameInvoice = context.duplicateInvoiceDocumentId() != null;
        if (!sameFile && !sameInvoice) {
            return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, true, null));
        }

        StringBuilder message = new StringBuilder();
        if (sameFile) {
            message.append(
                    "O mesmo ficheiro já foi submetido no documento %s.".formatted(context.duplicateFileDocumentId()));
        }
        if (sameInvoice) {
            if (!message.isEmpty()) {
                message.append(' ');
            }
            message.append("Já existe uma fatura aprovada com o mesmo NIF e número no documento %s."
                    .formatted(context.duplicateInvoiceDocumentId()));
        }
        return Optional.of(new RuleOutcome(ValidationSeverity.WARNING, false, message.toString()));
    }
}
