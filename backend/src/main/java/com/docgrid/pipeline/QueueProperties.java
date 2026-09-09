package com.docgrid.pipeline;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A fila de processamento e a sua dead-letter queue.
 *
 * <p>O mesmo padrão de {@code docgrid.storage}: com {@code endpoint} definido aponta-se ao
 * LocalStack com credenciais estáticas; sem ele, à AWS com a cadeia de credenciais por
 * omissão. O {@code visibilityTimeout} não aparece aqui de propósito: é um atributo da
 * fila, criado pela infraestrutura (script de init em local, Terraform em AWS), e a
 * aplicação não tem de o conhecer.
 *
 * @param name a fila principal, de onde o worker recebe os eventos do S3
 * @param dlqName a dead-letter queue, onde a mensagem acaba depois de esgotar as tentativas
 * @param region a região do SQS
 * @param endpoint presente só fora da AWS (LocalStack, testes); vazio ⇒ AWS por omissão
 * @param accessKey chave de acesso, só usada quando há {@code endpoint}
 * @param secretKey par de {@code accessKey}, com as mesmas regras
 * @param maxReceiveCount entregas antes de a mensagem desistir — tem de bater certo com o
 *     {@code maxReceiveCount} da política de redrive que a infraestrutura cria na fila
 * @param waitTime o long polling: tempo que uma receção vazia espera por uma mensagem,
 *     em vez de voltar a perguntar logo
 */
@ConfigurationProperties("docgrid.queue")
public record QueueProperties(
        String name,
        String dlqName,
        String region,
        String endpoint,
        String accessKey,
        String secretKey,
        int maxReceiveCount,
        Duration waitTime) {

    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
