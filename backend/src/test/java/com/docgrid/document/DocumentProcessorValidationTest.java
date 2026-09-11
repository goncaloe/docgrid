package com.docgrid.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Prova o critério de aceitação de ponta a ponta: uma fatura com IVA que não bate certo
 * atravessa o pipeline inteiro e fica {@code NEEDS_REVIEW}, com o motivo no evento.
 *
 * <p>Um contexto Spring próprio (fixture diferente) só para este caso — a confiança mínima
 * e o campo em falta já têm cobertura unitária exaustiva ao nível da regra; este teste
 * prova a ligação, não repete as regras.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "docgrid.extraction.stub-fixture=extraction/fixtures/bad-arithmetic.json")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class DocumentProcessorValidationTest {

    private static final String BUCKET = LocalStackPipelineConfiguration.BUCKET;

    @Autowired
    private DocumentProcessor processor;

    @Autowired
    private DocumentUploadService uploads;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private S3Client s3;

    @Test
    void anInvoiceWithBadArithmeticEndsUpNeedingReview() {
        byte[] content = "fatura com o total errado".getBytes(UTF_8);
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", (long) content.length));
        s3.putObject(
                request -> request.bucket(BUCKET).key(uploaded.storageKey()).contentType("application/pdf"),
                RequestBody.fromBytes(content));

        ProcessingOutcome outcome = processor.process(BUCKET, uploaded.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.PROCESSED);
        Document document = documents.findById(uploaded.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(uploaded.documentId()))
                .last()
                .satisfies(event -> {
                    assertThat(event.getToStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
                    assertThat(event.getReason()).contains("bate certo");
                });
    }
}
