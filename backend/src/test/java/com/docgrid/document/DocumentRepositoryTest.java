package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.support.RepositoryTest;

/** As consultas do agregado documento, contra um Postgres real. */
@RepositoryTest
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private TestEntityManager entityManager;

    private UUID organizationId;
    private UUID submitterId;

    @BeforeEach
    void setUp() {
        organizationId = AuthFixtures.organization(entityManager);
        submitterId = AuthFixtures.user(entityManager, organizationId);
    }

    @Test
    void findsTheReviewQueueOfAnOrganizationNewestFirst() {
        documentWith(DocumentStatus.UPLOADED, "org/a/1.pdf");
        Document older = documentWith(DocumentStatus.NEEDS_REVIEW, "org/a/2.pdf");
        Document newer = documentWith(DocumentStatus.NEEDS_REVIEW, "org/a/3.pdf");
        entityManager.flush();

        Page<Document> queue = documents.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                organizationId, DocumentStatus.NEEDS_REVIEW, PageRequest.of(0, 10));

        assertThat(queue.getContent()).containsExactly(newer, older);
        assertThat(documents.countByOrganizationIdAndStatus(organizationId, DocumentStatus.NEEDS_REVIEW))
                .isEqualTo(2);
    }

    @Test
    void combinesSeveralStatusesInOneQueue() {
        documentWith(DocumentStatus.NEEDS_REVIEW, "org/a/1.pdf");
        documentWith(DocumentStatus.EXTRACTED, "org/a/2.pdf");
        documentWith(DocumentStatus.REJECTED, "org/a/3.pdf");
        entityManager.flush();

        Page<Document> queue = documents.findByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                organizationId, List.of(DocumentStatus.NEEDS_REVIEW, DocumentStatus.EXTRACTED), PageRequest.of(0, 10));

        assertThat(queue.getTotalElements()).isEqualTo(2);
    }

    @Test
    void neverReturnsADocumentOfAnotherOrganization() {
        Document document = documentWith(DocumentStatus.UPLOADED, "org/a/1.pdf");
        UUID otherOrganization = AuthFixtures.organization(entityManager);
        entityManager.flush();

        assertThat(documents.findByIdAndOrganizationId(document.getId(), organizationId))
                .contains(document);
        assertThat(documents.findByIdAndOrganizationId(document.getId(), otherOrganization))
                .isEmpty();
    }

    @Test
    void findsTheDuplicateInvoiceOfTheSameSupplier() {
        Document original = documentWith(DocumentStatus.APPROVED, "org/a/1.pdf");
        original.projectInvoiceFields(new InvoiceFields(
                "501442889",
                "FT 2026/117",
                LocalDate.of(2026, 8, 14),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        entityManager.flush();

        assertThat(documents.findByOrganizationIdAndSupplierTaxIdAndInvoiceNumber(
                        organizationId, "501442889", "FT 2026/117"))
                .containsExactly(original);
        assertThat(documents.findByOrganizationIdAndSupplierTaxIdAndInvoiceNumber(
                        organizationId, "501442889", "FT 2026/118"))
                .isEmpty();
    }

    @Test
    void findsTheDuplicateFileByItsHash() {
        Document document = documentWith(DocumentStatus.UPLOADED, "org/a/1.pdf");
        document.recordUploadedFile(48_213L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        entityManager.flush();

        assertThat(documents.findByOrganizationIdAndFileHash(
                        organizationId, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
                .containsExactly(document);
    }

    @Test
    void refusesTwoDocumentsForTheSameObjectInTheBucket() {
        documentWith(DocumentStatus.UPLOADED, "org/a/mesma-chave.pdf");
        entityManager.flush();

        Document duplicate =
                new Document(organizationId, submitterId, "org/a/mesma-chave.pdf", "outro-nome.pdf", "application/pdf");

        assertThat(documents.existsByStorageKey("org/a/mesma-chave.pdf")).isTrue();
        assertThatThrownBy(() -> documents.saveAndFlush(duplicate))
                .as("é esta constraint que torna possível a idempotência do worker")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsADocumentByTheKeyThatCameInTheQueueMessage() {
        Document document = documentWith(DocumentStatus.UPLOADED, "org/a/1.pdf");
        entityManager.flush();

        assertThat(documents.findByStorageKey("org/a/1.pdf")).contains(document);
        assertThat(documents.findByStorageKey("org/a/inexistente.pdf")).isEmpty();
    }

    private Document documentWith(DocumentStatus status, String storageKey) {
        Document document = new Document(organizationId, submitterId, storageKey, "fatura.pdf", "application/pdf");
        walkTo(document, status);
        return entityManager.persistAndFlush(document);
    }

    /** Leva o documento até ao estado pedido pelo caminho que o ciclo de vida permite. */
    private static void walkTo(Document document, DocumentStatus target) {
        List<DocumentStatus> path =
                switch (target) {
                    case UPLOADED -> List.of();
                    case PROCESSING -> List.of(DocumentStatus.PROCESSING);
                    case EXTRACTED -> List.of(DocumentStatus.PROCESSING, DocumentStatus.EXTRACTED);
                    case NEEDS_REVIEW -> List.of(DocumentStatus.PROCESSING, DocumentStatus.NEEDS_REVIEW);
                    case FAILED -> List.of(DocumentStatus.PROCESSING, DocumentStatus.FAILED);
                    case APPROVED ->
                        List.of(DocumentStatus.PROCESSING, DocumentStatus.EXTRACTED, DocumentStatus.APPROVED);
                    case REJECTED ->
                        List.of(DocumentStatus.PROCESSING, DocumentStatus.NEEDS_REVIEW, DocumentStatus.REJECTED);
                    case EXPORTED ->
                        List.of(
                                DocumentStatus.PROCESSING,
                                DocumentStatus.EXTRACTED,
                                DocumentStatus.APPROVED,
                                DocumentStatus.EXPORTED);
                };
        path.forEach(step -> document.transitionTo(step, Actor.system(), null));
    }
}
