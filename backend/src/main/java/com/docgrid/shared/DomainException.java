package com.docgrid.shared;

/**
 * Base de todas as exceções de domínio.
 *
 * <p>Uma regra de negócio violada lança uma subclasse desta e nunca uma exceção genérica.
 * É o que permite à etapa 06 traduzi-las todas para respostas RFC 7807 num só sítio, sem
 * ter de conhecer cada caso à mão.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
