package com.docgrid.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.InvalidParameterException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

/**
 * As duas categorias e nenhuma mais: ilegível é permanente (o processor falha o documento
 * logo), indisponível é transitório (o SQS repete). Um caso novo do Textract que não
 * caiba numa das duas é um bug — e sai desta classe a apontar ao sítio certo.
 */
class TextractErrorTest {

    @Test
    void aDocumentTheServiceCannotReadIsPermanent() {
        for (Throwable cause : new Throwable[] {
            UnsupportedDocumentException.builder()
                    .message("formato não suportado")
                    .build(),
            BadDocumentException.builder().message("não é uma fatura").build(),
            InvalidParameterException.builder().message("bytes vazios").build()
        }) {
            RuntimeException translated = TextractError.translate(cause);

            assertThat(translated)
                    .as(cause.getClass().getSimpleName())
                    .isInstanceOf(UnreadableDocumentException.class)
                    .hasMessageContaining("ilegível");
        }
    }

    @Test
    void anUnavailableServiceIsTransitory() {
        RuntimeException translated = TextractError.translate(new IllegalStateException("connection reset"));

        assertThat(translated)
                .as("um erro que não é do documento é do serviço: repetir faz sentido")
                .isNotInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("indisponível");
        assertThat(translated.getCause()).isInstanceOf(IllegalStateException.class);
    }
}
