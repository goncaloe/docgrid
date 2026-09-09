package com.docgrid.document;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Política de upload: quanto tempo dura a autorização, que tamanho se aceita e que tipos.
 *
 * @param urlTtl validade dos URLs pré-assinados (escrita e leitura)
 * @param maxFileSize tamanho máximo declarado no pedido
 * @param allowedTypes {@code Content-Type} aceite → extensão canónica do ficheiro
 */
@ConfigurationProperties("docgrid.upload")
public record UploadProperties(Duration urlTtl, DataSize maxFileSize, Map<String, String> allowedTypes) {

    public UploadProperties {
        allowedTypes = allowedTypes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(allowedTypes));
    }
}
