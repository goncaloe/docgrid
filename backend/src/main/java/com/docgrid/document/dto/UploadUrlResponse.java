package com.docgrid.document.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A autorização devolvida ao cliente: o id do documento já registado, e para onde, como e
 * até quando pode enviar o ficheiro.
 *
 * @param requiredHeaders cabeçalhos que o {@code PUT} tem de repetir (o {@code Content-Type}
 *     vai assinado, portanto tem de bater certo)
 */
public record UploadUrlResponse(
        UUID documentId,
        String storageKey,
        URI uploadUrl,
        String httpMethod,
        Map<String, String> requiredHeaders,
        Instant expiresAt) {}
