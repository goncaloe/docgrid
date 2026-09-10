package com.docgrid.extraction;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.TextractClientBuilder;

/**
 * O cliente do Textract, montado a partir de {@link ExtractionProperties} — o mesmo
 * desenho do {@code SqsConfig} e do {@code StorageConfig}: com {@code endpoint} definido
 * aponta-se a um recetor próprio com credenciais estáticas; sem ele, à AWS com a cadeia
 * de credenciais por omissão.
 *
 * <p>Só existe no perfil {@code aws}: em local e nos testes o bean nem é criado —
 * {@code @Profile} exclusivos, de propósito. As análises pagam-se por documento; nada
 * aqui arranca sem a AWS estar lá.
 *
 * <p>O {@code apiCallTimeout} está fixo por propriedade, e não por retry policy: uma
 * análise de fatura demora segundos, não minutos — quem estourar o timeout sai como erro
 * transitório e o SQS volta a tentar.
 */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
class TextractConfig {

    @Bean
    TextractClient textractClient(ExtractionProperties props) {
        ExtractionProperties.Textract textract = props.textract();
        TextractClientBuilder builder = TextractClient.builder()
                .region(Region.of(textract.region()))
                .credentialsProvider(credentials(textract))
                .overrideConfiguration(o -> o.apiCallTimeout(textract.apiTimeout()));
        if (textract.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(textract.endpoint()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(ExtractionProperties.Textract props) {
        if (!props.hasCustomEndpoint()) {
            return DefaultCredentialsProvider.create();
        }
        String accessKey = props.accessKey() == null || props.accessKey().isBlank() ? "test" : props.accessKey();
        String secretKey = props.secretKey() == null || props.secretKey().isBlank() ? "test" : props.secretKey();
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
