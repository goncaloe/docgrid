package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.util.unit.DataSize;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.storage.PresignedUrl;
import com.docgrid.storage.StorageService;
import com.docgrid.support.RepositoryTest;

/**
 * O registo do documento e a validação de entrada, contra um Postgres real. O S3 fica de
 * fora — {@link com.docgrid.storage.S3StorageServiceTest} cobre-o a sério, e
 * {@code DocumentUploadFlowTest} liga as duas pontas.
 */
@RepositoryTest
class DocumentUploadServiceTest {

    private static final Clock SEPT_2026 = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Map<String, String> ALLOWED_TYPES =
            Map.of("application/pdf", "pdf", "image/jpeg", "jpg", "image/png", "png");

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private TestEntityManager entityManager;

    private final RecordingStorage storage = new RecordingStorage();
    private UUID organizationId;
    private UUID userId;
    private DocumentUploadService service;

    @BeforeEach
    void setUp() {
        organizationId = AuthFixtures.organization(entityManager);
        userId = AuthFixtures.user(entityManager, organizationId);
        entityManager.flush();
        UploadProperties properties =
                new UploadProperties(Duration.ofMinutes(5), DataSize.ofMegabytes(10), ALLOWED_TYPES);
        service = new DocumentUploadService(
                documents, events, storage, new FixedUser(organizationId, userId), SEPT_2026, properties);
    }

    @Test
    void registersADocumentInUploadedWithACreatedEvent() {
        UploadUrlResponse response =
                service.authorizeUpload(new UploadUrlRequest("Fatura Janeiro.pdf", "application/pdf", 1_234L));
        entityManager.flush();
        entityManager.clear();

        Document document = documents.findById(response.documentId()).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getStorageKey())
                .isEqualTo("org/%s/2026/09/%s.pdf".formatted(organizationId, response.documentId()));
        assertThat(document.getOriginalFilename()).isEqualTo("Fatura Janeiro.pdf");
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(document.getSizeBytes()).isNull();
        assertThat(document.getFileHash()).isNull();

        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(response.documentId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(DocumentEventType.CREATED);
                    assertThat(event.getToStatus()).isEqualTo(DocumentStatus.UPLOADED);
                    assertThat(event.getActorType()).isEqualTo(ActorType.USER);
                    assertThat(event.getActorUserId()).isEqualTo(userId);
                });
    }

    @Test
    void asksStorageToPresignAPutOnTheDocumentsKey() {
        UploadUrlResponse response = service.authorizeUpload(new UploadUrlRequest("recibo.png", "image/png", 4_096L));

        assertThat(storage.key).isEqualTo(response.storageKey());
        assertThat(storage.contentType).isEqualTo("image/png");
        assertThat(storage.ttl).isEqualTo(Duration.ofMinutes(5));
        assertThat(response.httpMethod()).isEqualTo("PUT");
        assertThat(response.uploadUrl()).isEqualTo(RecordingStorage.URL);
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/png");
    }

    @Test
    void takesTheExtensionFromTheContentTypeNotTheFilename() {
        UploadUrlResponse response = service.authorizeUpload(new UploadUrlRequest("FOTO.JPEG", "image/jpeg", 10L));

        assertThat(response.storageKey()).endsWith(".jpg");
    }

    @Test
    void rejectsADisallowedContentType() {
        assertThatThrownBy(() ->
                        service.authorizeUpload(new UploadUrlRequest("virus.exe", "application/x-msdownload", 10L)))
                .isInstanceOf(UploadValidationException.class)
                .hasMessageContaining("não aceite");

        assertThat(documents.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    void rejectsAnExtensionThatContradictsTheContentType() {
        assertThatThrownBy(() -> service.authorizeUpload(new UploadUrlRequest("fatura.png", "application/pdf", 10L)))
                .isInstanceOf(UploadValidationException.class)
                .hasMessageContaining("não corresponde");
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        long elevenMega = 11L * 1024 * 1024;

        assertThatThrownBy(() ->
                        service.authorizeUpload(new UploadUrlRequest("enorme.pdf", "application/pdf", elevenMega)))
                .isInstanceOf(UploadValidationException.class)
                .hasMessageContaining("grande demais");
    }

    private record FixedUser(UUID organizationId, UUID userId) implements CurrentUserProvider {
        @Override
        public UUID currentOrganizationId() {
            return organizationId;
        }

        @Override
        public UUID currentUserId() {
            return userId;
        }
    }

    private static final class RecordingStorage implements StorageService {

        static final URI URL = URI.create("https://s3.example/upload");

        private String key;
        private String contentType;
        private Duration ttl;

        @Override
        public PresignedUrl createUploadUrl(String key, String contentType, Duration ttl) {
            this.key = key;
            this.contentType = contentType;
            this.ttl = ttl;
            return new PresignedUrl(
                    URL,
                    "PUT",
                    Map.of("Content-Type", contentType),
                    Instant.now().plus(ttl));
        }

        @Override
        public PresignedUrl createDownloadUrl(String key, String downloadFilename, Duration ttl) {
            return new PresignedUrl(
                    URI.create("https://s3.example/download"),
                    "GET",
                    Map.of(),
                    Instant.now().plus(ttl));
        }

        @Override
        public boolean exists(String key) {
            return false;
        }
    }
}
