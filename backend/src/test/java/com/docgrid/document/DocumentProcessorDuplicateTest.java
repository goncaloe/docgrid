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

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.auth.UserRole;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * A regra de duplicado, de ponta a ponta. Contexto Spring isolado (fixture própria) —
 * o teste aprova um documento, o que deixa dados duráveis que não podem escapar para o
 * contexto por omissão que {@code DocumentProcessorTest}, {@code PipelineFlowTest} e
 * {@code DlqRedriveTest} partilham entre si.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "docgrid.extraction.stub-fixture=extraction/fixtures/duplicate-check.json")
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class, DemoIdentityConfiguration.class})
class DocumentProcessorDuplicateTest {

    private static final String BUCKET = LocalStackPipelineConfiguration.BUCKET;

    @Autowired
    private DocumentProcessor processor;

    @Autowired
    private DocumentApprovalService approvals;

    @Autowired
    private DocumentUploadService uploads;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private S3Client s3;

    @Autowired
    private CurrentUserProvider currentUser;

    @Test
    void submittingTheSameApprovedInvoiceTwiceFlagsItAsDuplicate() {
        UploadUrlResponse first = registerAndStore("primeira entrega desta fatura".getBytes(UTF_8));
        processor.process(BUCKET, first.storageKey(), false);
        approvals.approve(
                first.documentId(), currentUser.currentOrganizationId(), Actor.system(), UserRole.FINANCE, null);

        UploadUrlResponse second = registerAndStore("segunda vez, mesma fatura, ficheiro diferente".getBytes(UTF_8));
        ProcessingOutcome outcome = processor.process(BUCKET, second.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.PROCESSED);
        Document document = documents.findById(second.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(second.documentId()))
                .last()
                .satisfies(event -> assertThat(event.getReason())
                        .contains(first.documentId().toString()));
    }

    @Test
    void submittingTheExactSameFileTwiceFlagsItAsDuplicate() {
        byte[] content = "exatamente o mesmo ficheiro, duas vezes".getBytes(UTF_8);
        UploadUrlResponse first = registerAndStore(content);
        processor.process(BUCKET, first.storageKey(), false);

        UploadUrlResponse second = registerAndStore(content);
        ProcessingOutcome outcome = processor.process(BUCKET, second.storageKey(), false);

        assertThat(outcome).isEqualTo(ProcessingOutcome.PROCESSED);
        Document document = documents.findById(second.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(second.documentId()))
                .last()
                .satisfies(event -> assertThat(event.getReason())
                        .contains(first.documentId().toString()));
    }

    private UploadUrlResponse registerAndStore(byte[] content) {
        UploadUrlResponse uploaded =
                uploads.authorizeUpload(new UploadUrlRequest("fatura.pdf", "application/pdf", (long) content.length));
        s3.putObject(
                request -> request.bucket(BUCKET).key(uploaded.storageKey()).contentType("application/pdf"),
                RequestBody.fromBytes(content));
        return uploaded;
    }
}
