package com.docgrid.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.DocumentProbe;
import com.docgrid.document.DocumentProcessor;
import com.docgrid.document.DocumentUploadService;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.pipeline.dto.RedriveSummary;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Os critérios de aceitação nº 3 e nº 4: um documento que falha sempre acaba na DLQ e
 * fica {@code FAILED}, e a DLQ é inspecionável e reprocessável — e reprocessar leva mesmo
 * o documento até ao fim.
 *
 * <p>Contexto próprio, com uma fila de teste feita à medida: {@code maxReceiveCount=1} e
 * timeout de visibilidade a zero, para o redrive acontecer em milissegundos em vez dos
 * 120 s da fila real. O que se testa é a mecânica, não a paciência.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({
    PostgresContainerConfiguration.class,
    DlqRedriveTest.FastRedriveLocalStack.class,
    DemoIdentityConfiguration.class
})
class DlqRedriveTest {

    @Autowired
    private DocumentUploadService uploads;

    @Autowired
    private SqsQueues queues;

    @Autowired
    private DocumentProcessor processor;

    @Autowired
    private S3EventNotificationParser parser;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private DlqAdmin dlqAdmin;

    @Autowired
    private S3Client s3;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aFailingDocumentReachesTheDlqAndIsPickedUpAgainByARedrive() {
        // O documento é registado, mas o ficheiro ainda não chegou ao S3: falha permanente.
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", 64L));
        queues.sendToMain(objectCreatedEvent(uploaded.storageKey()));

        DocumentWorker worker = new DocumentWorker(queues, processor, parser, queueProperties);

        // 1ª entrega: o processamento falha, a mensagem volta à fila (não é apagada).
        worker.drainOnce();
        assertThat(DocumentProbe.status(jdbc, uploaded.documentId())).isEqualTo("FAILED");

        // Esgotado o maxReceiveCount, o SQS move a mensagem para a DLQ, onde é inspecionável.
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    worker.drainOnce();
                    assertThat(dlqAdmin.list()).isNotEmpty();
                });
        assertThat(dlqAdmin.list())
                .singleElement()
                .satisfies(message -> assertThat(message.storageKey()).isEqualTo(uploaded.storageKey()));

        // A causa é tratada: o ficheiro passa a existir.
        s3.putObject(
                request -> request.bucket(FastRedriveLocalStack.BUCKET).key(uploaded.storageKey()),
                RequestBody.fromString("conteúdo que agora existe"));

        // Reprocessar: a mensagem volta à fila, o documento é reaberto, e a ronda seguinte
        // do worker leva-o até EXTRACTED.
        RedriveSummary summary = dlqAdmin.redriveAll();
        assertThat(summary.movedToMainQueue()).isEqualTo(1);
        assertThat(dlqAdmin.list()).isEmpty();
        assertThat(DocumentProbe.status(jdbc, uploaded.documentId())).isEqualTo("PROCESSING");

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    worker.drainOnce();
                    assertThat(DocumentProbe.status(jdbc, uploaded.documentId()))
                            .isEqualTo("EXTRACTED");
                });
    }

    private static String objectCreatedEvent(String storageKey) {
        return """
                {"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"%s"},"object":{"key":"%s"}}}]}""".formatted(FastRedriveLocalStack.BUCKET, storageKey);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FastRedriveLocalStack {

        static final String BUCKET = "docgrid-documents";
        static final String QUEUE = "docgrid-fast-processing";
        static final String DLQ = "docgrid-fast-processing-dlq";

        @Bean
        LocalStackContainer localStackContainer() {
            LocalStackContainer container = new LocalStackContainer(
                            DockerImageName.parse("localstack/localstack:4.9.2"))
                    .withServices(Service.S3, Service.SQS)
                    .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0")
                    .withEnv("SQS_ENDPOINT_STRATEGY", "path");
            container.start();
            provision(container);
            return container;
        }

        @Bean
        DynamicPropertyRegistrar fastRedriveProperties(LocalStackContainer container) {
            return registry -> {
                String endpoint = container.getEndpoint().toString();
                registry.add("docgrid.storage.endpoint", () -> endpoint);
                registry.add("docgrid.storage.region", container::getRegion);
                registry.add("docgrid.storage.access-key", container::getAccessKey);
                registry.add("docgrid.storage.secret-key", container::getSecretKey);
                registry.add("docgrid.storage.bucket", () -> BUCKET);
                registry.add("docgrid.queue.endpoint", () -> endpoint);
                registry.add("docgrid.queue.region", container::getRegion);
                registry.add("docgrid.queue.access-key", container::getAccessKey);
                registry.add("docgrid.queue.secret-key", container::getSecretKey);
                registry.add("docgrid.queue.name", () -> QUEUE);
                registry.add("docgrid.queue.dlq-name", () -> DLQ);
                registry.add("docgrid.queue.max-receive-count", () -> 1);
                registry.add("docgrid.queue.wait-time", () -> "1s");
            };
        }

        private static void provision(LocalStackContainer container) {
            AwsBasicCredentials credentials =
                    AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey());
            Region region = Region.of(container.getRegion());
            try (S3Client s3 = S3Client.builder()
                            .endpointOverride(container.getEndpoint())
                            .region(region)
                            .credentialsProvider(StaticCredentialsProvider.create(credentials))
                            .forcePathStyle(true)
                            .build();
                    SqsClient sqs = SqsClient.builder()
                            .endpointOverride(container.getEndpoint())
                            .region(region)
                            .credentialsProvider(StaticCredentialsProvider.create(credentials))
                            .build()) {

                s3.createBucket(bucket -> bucket.bucket(BUCKET));

                String dlqUrl = sqs.createQueue(queue -> queue.queueName(DLQ)).queueUrl();
                String dlqArn = sqs.getQueueAttributes(
                                attributes -> attributes.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                        .attributes()
                        .get(QueueAttributeName.QUEUE_ARN);

                sqs.createQueue(queue -> queue.queueName(QUEUE)
                        .attributes(Map.of(
                                QueueAttributeName.VISIBILITY_TIMEOUT,
                                "0",
                                QueueAttributeName.REDRIVE_POLICY,
                                "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"1\"}".formatted(dlqArn))));
            }
        }
    }
}
