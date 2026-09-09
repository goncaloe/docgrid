package com.docgrid.document;

import java.util.UUID;

import com.docgrid.shared.DomainException;

/**
 * Documento inexistente. A etapa 06 traduz isto para 404 — inclusive quando o documento
 * existe mas é de outra organização, para a resposta não revelar que existe.
 */
public class DocumentNotFoundException extends DomainException {

    public DocumentNotFoundException(UUID documentId) {
        super("Documento não encontrado: " + documentId);
    }
}
