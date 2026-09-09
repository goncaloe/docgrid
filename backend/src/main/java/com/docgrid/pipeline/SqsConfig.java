package com.docgrid.pipeline;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * O cliente do SQS, montado a partir de {@link QueueProperties} — o mesmo desenho de
 * {@code StorageConfig}: {@code endpoint} definido ({@code local}, testes) aponta ao
 * LocalStack com credenciais estáticas; sem ele, à AWS com a cadeia por omissão, sem
 * nada fixo no código.
 *
 * <p>O bean existe em todos os perfis: o worker precisa dele para consumir, e a API
 * precisa dele para administrar a DLQ (etapa 03).
 */
@Configuration(proxyBeanMethods = false)
class SqsConfig {

    @Bean
    SqsClient sqsClient(QueueProperties props) {
        return buildSqsClient(props);
    }

    static SqsClient buildSqsClient(QueueProperties props) {
        SqsClientBuilder builder =
                SqsClient.builder().region(Region.of(props.region())).credentialsProvider(credentials(props));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(QueueProperties props) {
        if (!props.hasCustomEndpoint()) {
            return DefaultCredentialsProvider.create();
        }
        String accessKey = props.accessKey() == null || props.accessKey().isBlank() ? "test" : props.accessKey();
        String secretKey = props.secretKey() == null || props.secretKey().isBlank() ? "test" : props.secretKey();
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
