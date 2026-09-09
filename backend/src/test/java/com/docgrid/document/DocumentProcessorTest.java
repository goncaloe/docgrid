package com.docgrid.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O coração da etapa: uma entrega da fila leva um documento de {@code UPLOADED} a
 * {@code EXTRACTED}, uma entrega repetida não faz o trabalho outra vez, e uma falha
 * deixa o documento num estado com significado.
 *
 * <p>Postgres e S3 a sério (LocalStack). O {@code StubExtractor} devolve a fatura fixa —
 * a extração de verdade é a etapa 04.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class DocumentProcessorTest {

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
    private ExtractedFieldRepository fields;

    @Autowired
    private ProcessingClaimRepository claims;

    @Autowired
    private S3Client s3;

    @Test
    void takesAnUploadedDocumentThroughToExtracted() {
        byte[] content = "conteúdo da fatura de setembro".getBytes(UTF_8);
        UploadUrlResponse uploaded = registerAndStore(content);

        ProcessingOutcome outcome = processor.process(BUCKET, uploaded.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.PROCESSED);
        Document document = documents.findById(uploaded.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
        assertThat(document.getSizeBytes()).isEqualTo((long) content.length);
        assertThat(document.getFileHash()).isEqualTo(sha256Hex(content));

        // A projeção de negócio e os campos com confiança (regra 6 do AGENTS.md).
        assertThat(document.getSupplierTaxId()).isEqualTo("505123452");
        assertThat(fields.findByDocumentId(uploaded.documentId())).hasSize(7);
        assertThat(fields.findByDocumentIdAndFieldName(uploaded.documentId(), ExtractedFieldName.SUPPLIER_TAX_ID))
                .get()
                .satisfies(field -> {
                    assertThat(field.getConfidence()).isEqualByComparingTo("0.97");
                    assertThat(field.getSource()).isEqualTo(FieldSource.AI);
                });

        // O histórico: nascimento, PROCESSING, EXTRACTED — e o claim concluído.
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(uploaded.documentId()))
                .extracting(DocumentEvent::getToStatus)
                .containsExactly(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING, DocumentStatus.EXTRACTED);
        assertThat(claims.findById(uploaded.storageKey()))
                .get()
                .satisfies(claim -> assertThat(claim.isCompleted()).isTrue());
    }

    @Test
    void aRepeatedDeliveryDoesTheWorkOnlyOnce() {
        byte[] content = "fatura entregue duas vezes".getBytes(UTF_8);
        UploadUrlResponse uploaded = registerAndStore(content);

        ProcessingOutcome first = processor.process(BUCKET, uploaded.storageKey(), false);
        ProcessingOutcome second = processor.process(BUCKET, uploaded.storageKey(), false);

        assertThat(first).isEqualTo(ProcessingOutcome.PROCESSED);
        assertThat(second).isEqualTo(ProcessingOutcome.DUPLICATE);
        assertThat(fields.findByDocumentId(uploaded.documentId())).hasSize(7);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(uploaded.documentId()))
                .filteredOn(event -> event.getToStatus() == DocumentStatus.EXTRACTED)
                .hasSize(1);
    }

    @Test
    void failsTheDocumentWhenTheObjectIsMissingFromS3() {
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", 128L));

        ProcessingOutcome outcome = processor.process(BUCKET, uploaded.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.FAILED);
        Document document = documents.findById(uploaded.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(uploaded.documentId()))
                .last()
                .satisfies(event -> {
                    assertThat(event.getToStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(event.getReason()).contains("não existe");
                });
    }

    @Test
    void failsWhenNoDocumentMatchesTheKey() {
        String orphanKey = "org/%s/2026/09/%s.pdf".formatted(UUID.randomUUID(), UUID.randomUUID());

        ProcessingOutcome outcome = processor.process(BUCKET, orphanKey, false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.FAILED);
        assertThat(documents.findByStorageKey(orphanKey)).isEmpty();
        assertThat(claims.findById(orphanKey)).isEmpty();
    }

    @Test
    void ignoresAnEventFromAnUnexpectedBucket() {
        byte[] content = "fatura noutro bucket".getBytes(UTF_8);
        UploadUrlResponse uploaded = registerAndStore(content);

        ProcessingOutcome outcome = processor.process("outro-bucket-qualquer", uploaded.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.FAILED);
        assertThat(documents.findById(uploaded.documentId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.UPLOADED);
    }

    private UploadUrlResponse registerAndStore(byte[] content) {
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", (long) content.length));
        s3.putObject(
                request -> request.bucket(BUCKET).key(uploaded.storageKey()).contentType("application/pdf"),
                RequestBody.fromBytes(content));
        return uploaded;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
