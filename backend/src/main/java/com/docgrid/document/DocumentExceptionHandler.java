package com.docgrid.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tradução mínima das exceções deste pacote para respostas de erro.
 *
 * <p>É o suficiente para a etapa 02: um tipo de ficheiro recusado devolve {@code 400} com
 * uma mensagem clara, um documento inexistente devolve {@code 404}. Os erros de validação
 * de entrada ({@code @Valid}) já saem como {@code application/problem+json} por causa de
 * {@code spring.mvc.problemdetails.enabled}.
 *
 * <p>A etapa 06 substitui isto por um tratamento RFC 7807 completo e global, a partir de
 * {@link com.docgrid.shared.DomainException}, sem conhecer cada caso à mão.
 */
@RestControllerAdvice
class DocumentExceptionHandler {

    @ExceptionHandler(UploadValidationException.class)
    ProblemDetail onUploadValidation(UploadValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail onDocumentNotFound(DocumentNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
