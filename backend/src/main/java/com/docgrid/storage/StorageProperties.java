package com.docgrid.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ligação ao armazenamento. Nada aqui é segredo: as credenciais reais chegam por variável
 * de ambiente e, em AWS, pela cadeia de credenciais por omissão.
 *
 * @param bucket o bucket onde os ficheiros vivem
 * @param region a região do S3
 * @param endpoint presente só fora da AWS (LocalStack, testes); vazio ⇒ endpoint e
 *     credenciais da AWS por omissão, sem nada fixo no código
 * @param accessKey chave de acesso, só usada quando há {@code endpoint} (LocalStack aceita
 *     qualquer valor); em AWS ignora-se e usa-se a cadeia por omissão
 * @param secretKey par de {@code accessKey}, com as mesmas regras
 */
@ConfigurationProperties("docgrid.storage")
public record StorageProperties(String bucket, String region, String endpoint, String accessKey, String secretKey) {

    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
