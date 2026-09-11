package com.docgrid.shared;

import org.springframework.http.HttpStatus;

/**
 * Base de todas as exceções de domínio.
 *
 * <p>Uma regra de negócio violada lança uma subclasse desta e nunca uma exceção genérica.
 * Cada subclasse diz o seu próprio {@link HttpStatus}: é o que permite ao
 * {@code GlobalExceptionHandler} traduzi-las todas para respostas RFC 7807 num só sítio,
 * sem ter de conhecer cada caso à mão.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
