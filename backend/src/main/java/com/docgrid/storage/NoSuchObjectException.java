package com.docgrid.storage;

/**
 * O objeto pedido não existe no bucket.
 *
 * <p>Distinta de {@code S3Exception} de propósito: o S3 é fortemente consistente desde
 * 2020, portanto um 404 depois de um evento {@code ObjectCreated} não é uma lentidão
 * eventual — é um facto permanente, e quem decide o que fazer com ele (o worker, etapa 03)
 * precisa de o distinguir de uma falha transitória sem conhecer o protocolo do S3.
 */
public class NoSuchObjectException extends RuntimeException {

    public NoSuchObjectException(String key) {
        super("Não existe objeto com a chave %s".formatted(key));
    }
}
