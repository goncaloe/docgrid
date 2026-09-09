package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * O comportamento do agregado, sem base de dados nem contexto Spring: a validação da
 * transição e o evento que ela produz são regras de domínio puras.
 */
class DocumentTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID SUBMITTER = UUID.randomUUID();

    @Test
    void startsUploadedBecauseTheFileHasNotBeenProcessedYet() {
        assertThat(newDocument().getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void refusesToMoveAnApprovedDocumentBackToProcessing() {
        Document document = approvedDocument();

        assertThatThrownBy(() -> document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), "reprocessar"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("APPROVED")
                .hasMessageContaining("PROCESSING");

        assertThat(document.getStatus())
                .as("uma transição recusada não pode deixar o documento a meio")
                .isEqualTo(DocumentStatus.APPROVED);
    }

    @Test
    void recordsWhereItCameFromWhereItWentWhoAskedAndWhy() {
        Document document = newDocument();
        UUID reviewer = UUID.randomUUID();
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.NEEDS_REVIEW, Actor.system(), "IVA não bate certo");

        DocumentEvent event = document.transitionTo(DocumentStatus.REJECTED, Actor.user(reviewer), "Não é uma fatura");

        assertThat(event.getEventType()).isEqualTo(DocumentEventType.STATUS_CHANGED);
        assertThat(event.getDocumentId()).isEqualTo(document.getId());
        assertThat(event.getFromStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(event.getToStatus()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(event.getActorType()).isEqualTo(ActorType.USER);
        assertThat(event.getActorUserId()).isEqualTo(reviewer);
        assertThat(event.getReason()).isEqualTo("Não é uma fatura");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void keepsTheConfidenceOfEveryFieldItWasGiven() {
        Document document = newDocument();

        ExtractedField supplier = ExtractedField.readByMachine(
                document, ExtractedFieldName.SUPPLIER_TAX_ID, "501442889", new java.math.BigDecimal("0.940"));
        ExtractedField category = ExtractedField.writtenByHuman(document, ExtractedFieldName.CATEGORY, "Refeições");

        assertThat(document.getExtractedFields()).containsExactly(supplier, category);
        assertThat(supplier.getSource()).isEqualTo(FieldSource.AI);
        assertThat(supplier.getConfidence()).isEqualByComparingTo("0.940");
        assertThat(category.getSource()).isEqualTo(FieldSource.HUMAN);
        assertThat(category.getConfidence())
                .as("uma pessoa não tem grau de confiança a declarar")
                .isNull();
    }

    @Test
    void refusesAConfidenceOutsideZeroToOne() {
        Document document = newDocument();

        assertThatThrownBy(() -> ExtractedField.readByMachine(
                        document, ExtractedFieldName.TOTAL_AMOUNT, "12.30", new java.math.BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 0 e 1");
    }

    @Test
    void refusesAUserActorWithoutAUser() {
        assertThatThrownBy(() -> new Actor(ActorType.USER, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(ActorType.SYSTEM, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Document newDocument() {
        return new Document(
                ORGANIZATION, SUBMITTER, "org/%s/2026/09/a.pdf".formatted(ORGANIZATION), "a.pdf", "application/pdf");
    }

    private static Document approvedDocument() {
        Document document = newDocument();
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.transitionTo(DocumentStatus.APPROVED, Actor.user(SUBMITTER), "Confere");
        return document;
    }
}
