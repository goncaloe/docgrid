package com.docgrid.document;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * Um documento só se corrige em {@code EXTRACTED} ou {@code NEEDS_REVIEW}. Um documento
 * {@code APPROVED} é imutável (ver {@code docs/01-PRODUCT.md}); qualquer outro estado ainda
 * não tem dados prontos para corrigir.
 */
public class DocumentNotEditableException extends DomainException {

    public DocumentNotEditableException(DocumentStatus status) {
        super(HttpStatus.CONFLICT, "Um documento em %s não pode ser corrigido".formatted(status));
    }
}
