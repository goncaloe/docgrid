package com.docgrid.export;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * Exportação inexistente. Traduz-se para 404 — inclusive quando a exportação existe mas
 * é de outra organização, para a resposta não revelar que existe.
 */
class ExportNotFoundException extends DomainException {

    ExportNotFoundException(UUID exportId) {
        super(HttpStatus.NOT_FOUND, "Exportação não encontrada: " + exportId);
    }
}
