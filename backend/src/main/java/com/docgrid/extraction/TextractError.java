package com.docgrid.extraction;

import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.InvalidParameterException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

/**
 * A tradução das falhas do Textract para as duas categorias que o worker distingue.
 *
 * <p>Um documento ilegível é permanente: repetir a chamada não o torna legível, e o
 * {@code DocumentProcessor} falha-o logo, sem retry. Uma indisponibilidade do serviço —
 * throttling, erro do lado deles, rede, timeout — é transitória: sai daqui como
 * {@code RuntimeException} genérica e o caminho existente da entrega SQS trata do resto
 * (3 tentativas, e a DLQ).
 *
 * <p>Os tipos específicos ficam presos a esta classe: quem usa o extractor não devolve
 * referência a tipos do SDK, nem sabe que eles existem.
 */
final class TextractError {

    private TextractError() {}

    static RuntimeException translate(Throwable cause) {
        boolean unreadable = cause instanceof UnsupportedDocumentException
                || cause instanceof BadDocumentException
                || cause instanceof InvalidParameterException;
        if (unreadable) {
            return new UnreadableDocumentException("Documento ilegível para o Textract: " + cause.getMessage());
        }
        // O resto — throttling, 5xx, rede, timeout — é transitório: o SQS devolve a
        // entrega à fila e repete, 3 tentativas, e a DLQ decide.
        return new RuntimeException("Serviço Textract indisponível: " + cause.getMessage(), cause);
    }

    private static RuntimeException unavailable(Throwable cause) {
        // O SQS devolve a entrega à fila e repete: 3 tentativas, e a DLQ decide.
        return new RuntimeException("Serviço Textract indisponível: " + cause.getMessage(), cause);
    }
}
