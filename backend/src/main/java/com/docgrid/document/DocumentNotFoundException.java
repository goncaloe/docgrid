package com.docgrid.document;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * Documento inexistente. Traduz-se para 404 — inclusive quando o documento existe mas é de
 * outra organização, para a resposta não revelar que existe.
 */
public class DocumentNotFoundException extends DomainException {

    public DocumentNotFoundException(UUID documentId) {
        super(HttpStatus.NOT_FOUND, "Documento não encontrado: " + documentId);
    }
}
