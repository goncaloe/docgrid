package com.docgrid.pipeline;

import java.util.Objects;

/**
 * O objeto a que um evento do S3 se refere — a única coisa que interessa da notificação
 * inteira.
 *
 * @param bucket o bucket onde o objeto foi criado
 * @param key a chave do objeto, já descodificada (o S3 envia-a url-encoded)
 */
public record S3ObjectReference(String bucket, String key) {

    public S3ObjectReference {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(key, "key");
    }
}
