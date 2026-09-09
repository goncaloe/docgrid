package com.docgrid.pipeline.dto;

/**
 * Uma mensagem parada na dead-letter queue, como se mostra a quem a inspeciona.
 *
 * <p>Quando a mensagem é um evento do S3 legível, {@code bucket} e {@code storageKey}
 * dizem que objeto ficou por processar. Quando não é — e uma DLQ é precisamente onde as
 * mensagens ilegíveis acabam —, esses campos são nulos e {@code rawPreview} traz o início
 * do corpo para dar uma pista.
 *
 * @param messageId o id da mensagem no SQS
 * @param bucket o bucket do objeto, se a mensagem for um evento do S3 legível
 * @param storageKey a chave do objeto, nas mesmas condições
 * @param approximateReceiveCount quantas vezes o SQS já a entregou
 * @param rawPreview o início do corpo, só quando a mensagem não é um evento do S3
 */
public record DlqMessageView(
        String messageId, String bucket, String storageKey, int approximateReceiveCount, String rawPreview) {}
