package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.support.RepositoryTest;

/**
 * O histórico de auditoria contra o Postgres, incluindo a coluna {@code jsonb}.
 *
 * <p>O {@code payload} merece um teste próprio: um mapa que atravessa o Hibernate até um
 * tipo {@code jsonb} e volta é o género de mapeamento que só falha em execução.
 */
@RepositoryTest
class DocumentEventRepositoryTest {

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private TestEntityManager entityManager;

    private UUID documentId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        UUID organizationId = AuthFixtures.organization(entityManager);
        reviewerId = AuthFixtures.user(entityManager, organizationId);
        documentId = DocumentFixtures.document(entityManager, organizationId, reviewerId);
        entityManager.flush();
    }

    @Test
    void keepsTheOldAndTheNewValueOfACorrectedFieldInTheJsonPayload() {
        events.save(DocumentEvent.fieldCorrected(
                documentId, ExtractedFieldName.VAT_AMOUNT, "23.00", "25.30", Actor.user(reviewerId)));
        entityManager.flush();
        entityManager.clear();

        DocumentEvent stored =
                events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId).get(0);

        assertThat(stored.getEventType()).isEqualTo(DocumentEventType.FIELD_CORRECTED);
        assertThat(stored.getPayload())
                .containsEntry("field", "VAT_AMOUNT")
                .containsEntry("from", "23.00")
                .containsEntry("to", "25.30");
        assertThat(stored.getFromStatus()).isNull();
        assertThat(stored.getToStatus()).isNull();
    }

    @Test
    void readsAnEmptyHistoryForADocumentNothingHappenedTo() {
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId)).isEmpty();
    }
}
