package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.support.RepositoryTest;

/**
 * O critério de aceitação da etapa: nenhuma transição passa sem deixar rasto.
 *
 * <p>{@code @DataJpaTest} não regista {@code @Service}, daí o {@code @Import} — que tem a
 * vantagem de deixar explícito que este teste leva o serviço a sério e o resto do contexto
 * não.
 */
@RepositoryTest
@Import(DocumentService.class)
class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private TestEntityManager entityManager;

    private UUID organizationId;
    private UUID submitterId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        organizationId = AuthFixtures.organization(entityManager);
        submitterId = AuthFixtures.user(entityManager, organizationId);
        documentId = DocumentFixtures.document(entityManager, organizationId, submitterId);
        entityManager.flush();
    }

    @Test
    void writesAnAuditEventForEveryTransition() {
        documentService.transition(documentId, DocumentStatus.PROCESSING, Actor.system(), null);
        documentService.transition(
                documentId, DocumentStatus.NEEDS_REVIEW, Actor.system(), "IVA não corresponde a nenhuma taxa");
        documentService.transition(documentId, DocumentStatus.APPROVED, Actor.user(submitterId), "Corrigido à mão");
        entityManager.flush();

        List<DocumentEvent> history = events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId);

        assertThat(history)
                .extracting(DocumentEvent::getFromStatus, DocumentEvent::getToStatus)
                .containsExactly(
                        tuple(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING),
                        tuple(DocumentStatus.PROCESSING, DocumentStatus.NEEDS_REVIEW),
                        tuple(DocumentStatus.NEEDS_REVIEW, DocumentStatus.APPROVED));
    }

    @Test
    void recordsWhoAskedForTheTransition() {
        documentService.transition(documentId, DocumentStatus.PROCESSING, Actor.system(), "mensagem da fila");
        documentService.transition(documentId, DocumentStatus.EXTRACTED, Actor.system(), null);
        documentService.transition(documentId, DocumentStatus.APPROVED, Actor.user(submitterId), "Confere");
        entityManager.flush();

        List<DocumentEvent> history = events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId);

        assertThat(history.get(0).getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(history.get(0).getActorUserId()).isNull();
        assertThat(history.get(0).getReason()).isEqualTo("mensagem da fila");
        assertThat(history.get(2).getActorType()).isEqualTo(ActorType.USER);
        assertThat(history.get(2).getActorUserId()).isEqualTo(submitterId);
        assertThat(history.get(2).getReason()).isEqualTo("Confere");
    }

    @Test
    void persistsTheNewStatusOnTheDocument() {
        documentService.transition(documentId, DocumentStatus.PROCESSING, Actor.system(), null);
        entityManager.flush();
        entityManager.clear();

        assertThat(documents.findById(documentId))
                .get()
                .extracting(Document::getStatus)
                .isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void writesNothingWhenTheTransitionIsRefused() {
        documentService.transition(documentId, DocumentStatus.PROCESSING, Actor.system(), null);
        documentService.transition(documentId, DocumentStatus.EXTRACTED, Actor.system(), null);
        documentService.transition(documentId, DocumentStatus.APPROVED, Actor.user(submitterId), "Confere");
        entityManager.flush();

        assertThatThrownBy(() ->
                        documentService.transition(documentId, DocumentStatus.PROCESSING, Actor.system(), "outra vez"))
                .isInstanceOf(InvalidStatusTransitionException.class);
        entityManager.clear();

        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId))
                .as("uma transição recusada não escreve histórico")
                .hasSize(3);
        assertThat(documents.findById(documentId))
                .get()
                .extracting(Document::getStatus)
                .isEqualTo(DocumentStatus.APPROVED);
    }

    @Test
    void refusesToTransitionADocumentThatDoesNotExist() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> documentService.transition(unknown, DocumentStatus.PROCESSING, Actor.system(), null))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }
}
