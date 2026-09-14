package com.docgrid.pipeline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.model.Message;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.DocumentProcessor;
import com.docgrid.document.DocumentUploadService;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.shared.Correlation;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O teste que interessa: o id de correlação sobrevive à passagem pela fila.
 *
 * <p>São dois caminhos, porque o produtor da mensagem do caminho normal é o S3 e um
 * evento {@code ObjectCreated} não carrega atributos nossos: no caminho do S3 o elo
 * entre a API e o worker é a base de dados ({@code correlation_id}); no caminho do que
 * a aplicação envia (o redrive da DLQ) o elo é o atributo de mensagem.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class CorrelationPropagationTest {

    private final HttpClient http = HttpClient.newHttpClient();

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

    @Test
    void correlationSurvivesTheQueueThroughTheDatabase() throws Exception {
        Correlation.set("upload-abc");
        try {
            UploadUrlResponse uploaded =
                    uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", 42L));

            HttpResponse<Void> put = http.send(
                    HttpRequest.newBuilder(uploaded.uploadUrl())
                            .header("Content-Type", "application/pdf")
                            // Conteúdo próprio deste teste: se fosse o mesmo do
                            // PipelineFlowTest, a regra de duplicados por hash binário
                            // marcaria o segundo documento como NEEDS_REVIEW.
                            .PUT(BodyPublishers.ofByteArray("conteúdo de uma fatura real (correlação)".getBytes(UTF_8)))
                            .build(),
                    BodyHandlers.discarding());
            assertThat(put.statusCode()).isEqualTo(200);

            // O MDC do fio de teste tem de estar limpo antes de o worker correr: upload
            // e worker correm na mesma JVM e no mesmo fio, e sem isto o teste passaria
            // por acidente. O MDC.clear() é parte do teste, não um detalhe.
            MDC.clear();

            Logger logger = (Logger) LoggerFactory.getLogger("com.docgrid");
            ListAppender<ILoggingEvent> logged = new ListAppender<>();
            logged.start();
            logger.addAppender(logged);
            try {
                DocumentWorker worker = new DocumentWorker(queues, processor, parser, queueProperties);
                await().atMost(Duration.ofSeconds(30))
                        .pollInterval(Duration.ofMillis(500))
                        .untilAsserted(() -> assertThat(worker.drainOnce()).isGreaterThan(0));

                assertThat(logged.list)
                        .filteredOn(event -> event.getLoggerName().equals(DocumentProcessor.class.getName()))
                        .anySatisfy(event ->
                                assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", "upload-abc"));
            } finally {
                logger.detachAppender(logged);
                logged.stop();
            }
        } finally {
            Correlation.clear();
        }
    }

    @Test
    void correlationSurvivesTheQueueAsAMessageAttribute() {
        Correlation.set("redrive-xyz");
        try {
            queues.sendToMain(OBJECT_CREATED_BODY);

            // Mesmo cuidado do outro caminho: o envio deixou o id no MDC do fio; o que
            // se afirma é que a mensagem o transporta, não que o fio ainda o tinha.
            MDC.clear();

            // O LocalStack deixa o s3:TestEvent do arranque na fila; o que se afirma
            // é que a nossa mensagem chega com o atributo, não que é a única.
            List<Message> received = queues.receiveFromMain();
            List<Message> withCorrelation = received.stream()
                    .filter(message -> message.messageAttributes().containsKey(Correlation.SQS_ATTRIBUTE))
                    .toList();
            assertThat(withCorrelation).hasSize(1);
            assertThat(withCorrelation
                            .get(0)
                            .messageAttributes()
                            .get(Correlation.SQS_ATTRIBUTE)
                            .stringValue())
                    .isEqualTo("redrive-xyz");

            // Nenhuma das recebidas se pode deixar na fila: a nossa, o worker da
            // próxima ronda ia processá-la contra uma chave sem documento.
            received.forEach(message -> queues.deleteFromMain(message.receiptHandle()));
        } finally {
            Correlation.clear();
        }
    }

    private static final String OBJECT_CREATED_BODY = """
            {"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"docgrid-documents"},"object":{"key":"org/x/2026/09/a.pdf"}}}]}""";
}
