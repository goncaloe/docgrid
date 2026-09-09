package com.docgrid.document;

import com.docgrid.shared.DomainException;

/**
 * Transição de estado recusada por {@link DocumentStatus}.
 *
 * <p>Guarda a origem e o destino para a etapa 06 poder construir uma resposta RFC 7807
 * que diga ao cliente o que tentou fazer, e não apenas que falhou.
 */
public class InvalidStatusTransitionException extends DomainException {

    private final DocumentStatus from;
    private final DocumentStatus to;

    InvalidStatusTransitionException(DocumentStatus from, DocumentStatus to) {
        super("Transição inválida: um documento em %s não pode passar a %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public DocumentStatus getFrom() {
        return from;
    }

    public DocumentStatus getTo() {
        return to;
    }
}
