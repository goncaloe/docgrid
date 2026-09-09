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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.DocumentProbe;
import com.docgrid.document.DocumentProcessor;
import com.docgrid.document.DocumentUploadService;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O critério de aceitação nº 1, automatizado: um ficheiro que chega ao S3 faz o documento
 * passar por {@code PROCESSING} sozinho, sem ninguém a empurrá-lo.
 *
 * <p>Ponta a ponta com a infraestrutura verdadeira: o upload direto para o S3 (URL
 * pré-assinado), a notificação S3→SQS que o {@code docker compose} configura, e o worker
 * a consumir a fila. Só o fio de fundo é que não corre — {@link DocumentWorker#drainOnce()}
 * dá-se aqui à mão, uma ronda de cada vez.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class PipelineFlowTest {

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

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aFileLandingInS3DrivesTheDocumentToExtracted() throws Exception {
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", 42L));

        HttpResponse<Void> put = http.send(
                HttpRequest.newBuilder(uploaded.uploadUrl())
                        .header("Content-Type", "application/pdf")
                        .PUT(BodyPublishers.ofByteArray("conteúdo de uma fatura real".getBytes(UTF_8)))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);

        DocumentWorker worker = new DocumentWorker(queues, processor, parser, queueProperties);

        // A notificação do S3 pode demorar uns instantes a aparecer na fila.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    worker.drainOnce();
                    assertThat(DocumentProbe.status(jdbc, uploaded.documentId()))
                            .isEqualTo("EXTRACTED");
                });

        assertThat(worker.drainOnce())
                .as("a fila fica vazia depois de a mensagem ser consumida")
                .isZero();
    }
}
