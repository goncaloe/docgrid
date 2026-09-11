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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.extraction.FieldGeometry;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * Corrigir um campo: origem HUMAN, valor anterior guardado, revalidação sem saltar por cima
 * do ciclo de vida. {@code @Transactional}: cada método arranca de uma base limpa, sem
 * documentos de outros métodos a interferir na DuplicateRule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, DemoIdentityConfiguration.class})
@Transactional
class FieldCorrectionServiceTest {

    @Autowired
    private FieldCorrectionService corrections;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private ExtractedFieldRepository fields;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private CurrentUserProvider currentUser;

    private UUID organizationId;
    private UUID submitterId;

    @BeforeEach
    void setUp() {
        organizationId = currentUser.currentOrganizationId();
        submitterId = currentUser.currentUserId();
    }

    @Test
    void correctingAFieldRecordsHumanOriginAndThePreviousValue() {
        UUID documentId = anExtractedDocument();

        corrections.correct(
                documentId, organizationId, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/99", Actor.user(submitterId));

        ExtractedField field = fields.findByDocumentIdAndFieldName(documentId, ExtractedFieldName.INVOICE_NUMBER)
                .orElseThrow();
        assertThat(field.getValueText()).isEqualTo("FT 2026/99");
        assertThat(field.getSource()).isEqualTo(FieldSource.HUMAN);
        assertThat(field.getConfidence()).isNull();

        List<DocumentEvent> history = events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId);
        DocumentEvent correction = history.get(history.size() - 1);
        assertThat(correction.getEventType()).isEqualTo(DocumentEventType.FIELD_CORRECTED);
        assertThat(correction.getPayload()).containsEntry("from", "FT 2026/1").containsEntry("to", "FT 2026/99");
    }

    @Test
    void correctingAFieldInNeedsReviewNeverAutoTransitionsBackToExtracted() {
        UUID documentId = anExtractedDocument();
        documents.findById(documentId).orElseThrow().transitionTo(DocumentStatus.NEEDS_REVIEW, Actor.system(), "teste");
        documents.flush();

        corrections.correct(
                documentId, organizationId, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/99", Actor.user(submitterId));

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void breakingAFieldOnAnExtractedDocumentSendsItToNeedsReview() {
        UUID documentId = anExtractedDocument();

        corrections.correct(
                documentId, organizationId, ExtractedFieldName.TOTAL_AMOUNT, "999999.00", Actor.user(submitterId));

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void refusesToCorrectAnApprovedDocument() {
        UUID documentId = anExtractedDocument();
        Document document = documents.findById(documentId).orElseThrow();
        events.save(document.transitionTo(DocumentStatus.APPROVED, Actor.user(submitterId), null));
        documents.flush();

        assertThatThrownBy(() -> corrections.correct(
                        documentId,
                        organizationId,
                        ExtractedFieldName.INVOICE_NUMBER,
                        "FT 2026/99",
                        Actor.user(submitterId)))
                .isInstanceOf(DocumentNotEditableException.class);
    }

    @Test
    void refusesAnInvalidAmount() {
        UUID documentId = anExtractedDocument();

        assertThatThrownBy(() -> corrections.correct(
                        documentId,
                        organizationId,
                        ExtractedFieldName.TOTAL_AMOUNT,
                        "não é um número",
                        Actor.user(submitterId)))
                .isInstanceOf(InvalidFieldValueException.class);
    }

    /**
     * Um documento limpo: todos os campos fiscais obrigatórios lidos pela máquina, com
     * confiança acima do limiar e sem nenhuma regra a falhar — para que só a correção em si
     * (e não uma falha pré-existente) explique o resultado de cada teste.
     */
    private UUID anExtractedDocument() {
        Document document = new Document(
                organizationId,
                submitterId,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        // Hash de ficheiro único por documento: sem isto, todos os documentos deste teste
        // partilhariam file_hash nulo dentro da mesma organização (a base de dados não se
        // limpa entre métodos) e a DuplicateRule apanhá-los-ia uns aos outros por engano.
        document.recordUploadedFile(100L, UUID.randomUUID().toString().repeat(2).substring(0, 64));
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                "Cantina do Zé, Lda.",
                "505123452",
                "FT " + UUID.randomUUID(),
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        document = documents.save(document);

        seedAiField(document, ExtractedFieldName.SUPPLIER_TAX_ID, "505123452");
        seedAiField(document, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/1");
        seedAiField(document, ExtractedFieldName.ISSUE_DATE, "2026-08-20");
        seedAiField(document, ExtractedFieldName.NET_AMOUNT, "100.00");
        seedAiField(document, ExtractedFieldName.VAT_AMOUNT, "23.00");
        seedAiField(document, ExtractedFieldName.VAT_RATE, "23.00");
        seedAiField(document, ExtractedFieldName.TOTAL_AMOUNT, "123.00");
        return document.getId();
    }

    /**
     * {@code readByMachine} já se regista em {@code document.addExtractedField(...)} — o
     * cascade de {@code Document} trata da escrita. Chamar {@code fields.save(...)} a
     * seguir faria um segundo {@code merge()} e associaria à sessão uma segunda instância
     * com o mesmo id, rebentando com "a different object with the same identifier".
     */
    private void seedAiField(Document document, ExtractedFieldName fieldName, String value) {
        ExtractedField.readByMachine(
                document,
                fieldName,
                value,
                new BigDecimal("0.99"),
                new FieldGeometry(
                        1,
                        List.of(
                                new FieldGeometry.Point(0.1, 0.1),
                                new FieldGeometry.Point(0.4, 0.1),
                                new FieldGeometry.Point(0.4, 0.2))));
    }
}
