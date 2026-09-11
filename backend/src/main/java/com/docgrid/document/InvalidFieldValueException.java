package com.docgrid.document;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/** O valor corrigido não tem o formato que o campo espera (data, montante). */
public class InvalidFieldValueException extends DomainException {

    public InvalidFieldValueException(ExtractedFieldName fieldName, String value) {
        super(HttpStatus.BAD_REQUEST, "Valor inválido para %s: \"%s\"".formatted(fieldName, value));
    }
}
