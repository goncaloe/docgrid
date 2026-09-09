package com.docgrid.storage;

import java.net.URI;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Os clientes do S3, montados a partir de {@link StorageProperties}.
 *
 * <p>Com {@code docgrid.storage.endpoint} definido (LocalStack, testes), aponta-se lá com
 * credenciais estáticas e acesso por caminho — o LocalStack não faz DNS por bucket. Sem
 * ele (AWS), usa-se o endpoint da região e a cadeia de credenciais por omissão; não há
 * nenhum valor fixo no código.
 *
 * <p>Os beans constroem-se sem tocar na rede: uma aplicação que nunca faz upload (um teste
 * de contexto, por exemplo) levanta-se à mesma. Os métodos {@code build*} são estáticos e
 * package-private de propósito — os testes de integração montam o cliente exatamente como
 * a aplicação o monta, sem duplicar a lógica.
 */
@Configuration(proxyBeanMethods = false)
class StorageConfig {

    private static final S3Configuration PATH_STYLE =
            S3Configuration.builder().pathStyleAccessEnabled(true).build();

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    S3Client s3Client(StorageProperties props) {
        return buildS3Client(props);
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties props) {
        return buildS3Presigner(props);
    }

    static S3Client buildS3Client(StorageProperties props) {
        S3ClientBuilder builder =
                S3Client.builder().region(Region.of(props.region())).credentialsProvider(credentials(props));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint())).serviceConfiguration(PATH_STYLE);
        }
        return builder.build();
    }

    static S3Presigner buildS3Presigner(StorageProperties props) {
        S3Presigner.Builder builder =
                S3Presigner.builder().region(Region.of(props.region())).credentialsProvider(credentials(props));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint())).serviceConfiguration(PATH_STYLE);
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties props) {
        if (!props.hasCustomEndpoint()) {
            return DefaultCredentialsProvider.create();
        }
        String accessKey = blankToDefault(props.accessKey());
        String secretKey = blankToDefault(props.secretKey());
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static String blankToDefault(String value) {
        return value == null || value.isBlank() ? "test" : value;
    }
}
