package com.docgrid.extraction;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * O motor não conseguiu ler o documento: uma foto esborratada, um PDF corrompido, uma
 * imagem sem qualquer texto.
 *
 * <p>É um erro permanente — repetir a chamada não vai fazer o documento ficar legível.
 * O {@code DocumentProcessor} falha o documento logo à primeira, sem esperar pelas três
 * tentativas do SQS: quem tem de intervir é uma pessoa, não um retry. Nunca atravessa a
 * fronteira de um controller REST, mas herda de {@code DomainException} como todo o resto.
 */
public class UnreadableDocumentException extends DomainException {

    public UnreadableDocumentException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
