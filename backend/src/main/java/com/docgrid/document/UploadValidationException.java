package com.docgrid.document;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * O pedido de autorização de upload não passou na validação de entrada: tipo não aceite,
 * extensão que não corresponde ao tipo, ou tamanho fora do limite.
 */
public class UploadValidationException extends DomainException {

    public UploadValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
