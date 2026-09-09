package com.docgrid.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Uma autorização temporária para o browser falar diretamente com o S3.
 *
 * @param url o endereço já assinado
 * @param httpMethod o método a usar ({@code PUT} para escrever, {@code GET} para ler)
 * @param requiredHeaders cabeçalhos que o pedido tem de repetir para a assinatura bater certo
 * @param expiresAt o instante a partir do qual o S3 recusa o pedido
 */
public record PresignedUrl(URI url, String httpMethod, Map<String, String> requiredHeaders, Instant expiresAt) {

    public PresignedUrl {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
