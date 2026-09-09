package com.docgrid.support;

import java.io.IOException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;

/**
 * O LocalStack (só S3) dos testes que exercitam o fluxo de upload, como bean.
 *
 * <p>Mesmo raciocínio de {@link PostgresContainerConfiguration}: um container por contexto
 * Spring, partilhado entre classes com a mesma configuração. Só os testes que importam
 * esta classe pagam o arranque — os de contexto e de repositório continuam sem LocalStack.
 *
 * <p>Não há {@code @ServiceConnection} para o S3 sem o Spring Cloud AWS, portanto o
 * container arranca aqui e um {@link DynamicPropertyRegistrar} injeta o endpoint e as
 * credenciais em {@code docgrid.storage.*}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalStackContainerConfiguration {

    /** O mesmo nome que o {@code docker-compose.yml} e o {@code application.yml} usam. */
    public static final String BUCKET = "docgrid-documents";

    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.9.2");

    @Bean
    LocalStackContainer localStackContainer() {
        LocalStackContainer container = new LocalStackContainer(IMAGE)
                .withServices(Service.S3)
                // Sem isto, o LocalStack ignora a assinatura e a expiração dos URLs
                // pré-assinados e serve qualquer objeto sem autenticação.
                .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
        container.start();
        createBucket(container);
        return container;
    }

    @Bean
    DynamicPropertyRegistrar localStackStorageProperties(LocalStackContainer container) {
        return registry -> {
            registry.add(
                    "docgrid.storage.endpoint", () -> container.getEndpoint().toString());
            registry.add("docgrid.storage.region", container::getRegion);
            registry.add("docgrid.storage.access-key", container::getAccessKey);
            registry.add("docgrid.storage.secret-key", container::getSecretKey);
            registry.add("docgrid.storage.bucket", () -> BUCKET);
        };
    }

    private static void createBucket(LocalStackContainer container) {
        try {
            var result = container.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("awslocal s3 mb falhou: " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar o bucket '%s' no LocalStack".formatted(BUCKET), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido a criar o bucket de testes", e);
        }
    }
}
