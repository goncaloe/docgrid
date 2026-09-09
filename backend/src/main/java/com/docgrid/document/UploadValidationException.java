package com.docgrid.document;

import com.docgrid.shared.DomainException;

/**
 * O pedido de autorização de upload não passou na validação de entrada: tipo não aceite,
 * extensão que não corresponde ao tipo, ou tamanho fora do limite.
 *
 * <p>A tradução para {@code 400} é feita por um {@code @RestControllerAdvice} mínimo nesta
 * etapa; a etapa 06 leva o tratamento de erros a sério (RFC 7807 completo).
 */
public class UploadValidationException extends DomainException {

    public UploadValidationException(String message) {
        super(message);
    }
}
