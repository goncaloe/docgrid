package com.docgrid.storage;

import java.time.Duration;

/**
 * O armazenamento dos ficheiros dos documentos.
 *
 * <p>É um mecanismo, não uma política: recebe uma chave e uma validade e devolve uma URL
 * assinada. Quem decide que chave usar, que tipos são aceites e quanto tempo dura a
 * autorização é a camada de documento.
 *
 * <p>A única implementação é o S3 ({@link S3StorageService}), apontado ao LocalStack em
 * desenvolvimento e testes e à AWS em produção. Não há implementação em memória: um teste
 * que passa contra um duplo e falha contra o S3 não vale nada (ver {@code docs/03-CONVENTIONS.md}).
 */
public interface StorageService {

    /**
     * Autoriza a escrita de um objeto nesta chave, com este {@code Content-Type}, durante
     * {@code ttl}. O cliente tem de enviar o mesmo {@code Content-Type} no {@code PUT}.
     */
    PresignedUrl createUploadUrl(String key, String contentType, Duration ttl);

    /**
     * Autoriza a leitura do objeto nesta chave durante {@code ttl}. A resposta do S3 vem
     * marcada como anexo com {@code downloadFilename}.
     */
    PresignedUrl createDownloadUrl(String key, String downloadFilename, Duration ttl);

    /** Se existe um objeto nesta chave. */
    boolean exists(String key);

    /**
     * O conteúdo do objeto nesta chave, para processamento (etapa 03: o worker lê-o para
     * calcular o hash e alimentar a extração).
     *
     * @throws NoSuchObjectException se o objeto não existir — um facto permanente, não
     *     uma lentidão eventual: o S3 é fortemente consistente desde 2020
     */
    StoredObject download(String key);
}
