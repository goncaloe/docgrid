package com.docgrid.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * O LocalStack com S3 <b>e</b> SQS dos testes do pipeline, como bean.
 *
 * <p>Mesmo raciocínio de {@link LocalStackContainerConfiguration}, com uma diferença que
 * paga o container maior: aqui corre o próprio script de arranque do {@code docker
 * compose} ({@code docker/localstack/init/ready.d/docgrid-resources.sh}), montado nas
 * hooks de init do LocalStack. Assim os testes exercitam exatamente a infraestrutura que
 * o desenvolvimento local usa — bucket, fila, dead-letter queue, política de redrive
 * ({@code maxReceiveCount=3}), timeout de visibilidade e notificação S3→SQS — e não uma
 * réplica em Java que podia divergir dela.
 *
 * <p>A espera é pela última linha que o script imprime: só aparece depois de o LocalStack
 * estar pronto e de todos os recursos existirem.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalStackPipelineConfiguration {

    public static final String BUCKET = "docgrid-documents";
    public static final String QUEUE = "docgrid-document-processing";
    public static final String DLQ = "docgrid-document-processing-dlq";
    public static final String REGION = "eu-west-1";

    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.9.2");

    /** Relativo ao diretório de trabalho do surefire, que é o módulo {@code backend/}. */
    private static final Path INIT_SCRIPT = Path.of(
                    "..", "docker", "localstack", "init", "ready.d", "docgrid-resources.sh")
            .toAbsolutePath()
            .normalize();

    @Bean
    LocalStackContainer localStackContainer() {
        if (!Files.isRegularFile(INIT_SCRIPT)) {
            throw new IllegalStateException("Script de arranque do LocalStack não encontrado em " + INIT_SCRIPT);
        }
        LocalStackContainer container = new LocalStackContainer(IMAGE)
                .withServices(Service.S3, Service.SQS)
                .withEnv("DEFAULT_REGION", REGION)
                .withEnv("AWS_DEFAULT_REGION", REGION)
                .withEnv("DOCGRID_BUCKET", BUCKET)
                .withEnv("DOCGRID_QUEUE", QUEUE)
                .withEnv("DOCGRID_DLQ", DLQ)
                // Sem isto o LocalStack ignora a assinatura e a expiração dos URLs
                // pré-assinados; com "path", os URLs das filas são previsíveis.
                .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0")
                .withEnv("SQS_ENDPOINT_STRATEGY", "path")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT, 0755),
                        "/etc/localstack/init/ready.d/docgrid-resources.sh")
                .waitingFor(Wait.forLogMessage(".*DocGrid: bucket .*\\n", 1).withStartupTimeout(Duration.ofMinutes(2)));
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar localStackPipelineProperties(LocalStackContainer container) {
        return registry -> {
            String endpoint = container.getEndpoint().toString();
            registry.add("docgrid.storage.endpoint", () -> endpoint);
            registry.add("docgrid.storage.region", () -> REGION);
            registry.add("docgrid.storage.access-key", container::getAccessKey);
            registry.add("docgrid.storage.secret-key", container::getSecretKey);
            registry.add("docgrid.storage.bucket", () -> BUCKET);
            registry.add("docgrid.queue.endpoint", () -> endpoint);
            registry.add("docgrid.queue.region", () -> REGION);
            registry.add("docgrid.queue.access-key", container::getAccessKey);
            registry.add("docgrid.queue.secret-key", container::getSecretKey);
            registry.add("docgrid.queue.name", () -> QUEUE);
            registry.add("docgrid.queue.dlq-name", () -> DLQ);
            // Uma espera curta: os testes não querem ficar 10 s parados numa receção vazia.
            registry.add("docgrid.queue.wait-time", () -> "1s");
        };
    }
}
