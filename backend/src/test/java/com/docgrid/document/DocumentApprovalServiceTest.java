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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.auth.UserRole;
import com.docgrid.extraction.FieldGeometry;
import com.docgrid.supplier.SupplierHistory;
import com.docgrid.supplier.SupplierHistoryProvider;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * A transição para {@code APPROVED} e a atualização do histórico do fornecedor — sem
 * pipeline nem S3, direto sobre as entidades (o worker constrói o documento por outro
 * caminho; aqui só interessa a aprovação em si).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, DemoIdentityConfiguration.class})
class DocumentApprovalServiceTest {

    private static final String SUPPLIER_TAX_ID = "505123452";

    @Autowired
    private DocumentApprovalService approvals;

    @Autowired
    private SupplierHistoryProvider supplierHistories;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentEventRepository events;

    @Autowired
    private ExtractedFieldRepository fields;

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
    void transitionsAnExtractedDocumentToApproved() {
        UUID documentId = anExtractedDocument(null);

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, null);

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId))
                .last()
                .satisfies(event -> assertThat(event.getToStatus()).isEqualTo(DocumentStatus.APPROVED));
    }

    @Test
    void doesNotTouchTheSupplierWhenNoCategoryIsChosen() {
        UUID documentId = anExtractedDocument("Cantina do Zé, Lda.");

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, null);

        assertThat(supplierHistories.historyFor(organizationId, SUPPLIER_TAX_ID))
                .isEqualTo(SupplierHistory.unknown());
    }

    @Test
    void createsTheSupplierOnTheFirstApprovalWithACategory() {
        UUID documentId = anExtractedDocument("Cantina do Zé, Lda.");

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, "Alimentação");

        SupplierHistory history = supplierHistories.historyFor(organizationId, SUPPLIER_TAX_ID);
        assertThat(history.usualCategory()).isEqualTo("Alimentação");
        assertThat(history.occurrenceCount()).isEqualTo(1);
    }

    @Test
    void aConsistentCategoryAcrossApprovalsGrowsTheStreak() {
        approvals.approve(
                anExtractedDocument("Cantina do Zé, Lda."),
                organizationId,
                Actor.system(),
                UserRole.FINANCE,
                "Alimentação");
        approvals.approve(
                anExtractedDocument("Cantina do Zé, Lda."),
                organizationId,
                Actor.system(),
                UserRole.FINANCE,
                "Alimentação");

        assertThat(supplierHistories.historyFor(organizationId, SUPPLIER_TAX_ID).occurrenceCount())
                .isEqualTo(2);
    }

    @Test
    void changingTheCategoryRestartsTheStreak() {
        approvals.approve(
                anExtractedDocument("Cantina do Zé, Lda."),
                organizationId,
                Actor.system(),
                UserRole.FINANCE,
                "Alimentação");
        approvals.approve(
                anExtractedDocument("Cantina do Zé, Lda."),
                organizationId,
                Actor.system(),
                UserRole.FINANCE,
                "Manutenção");

        SupplierHistory history = supplierHistories.historyFor(organizationId, SUPPLIER_TAX_ID);
        assertThat(history.usualCategory()).isEqualTo("Manutenção");
        assertThat(history.occurrenceCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheTaxIdWhenTheSupplierNameWasNeverExtracted() {
        UUID documentId = anExtractedDocument(null);

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, "Alimentação");

        assertThat(supplierHistories.historyFor(organizationId, SUPPLIER_TAX_ID).usualCategory())
                .isEqualTo("Alimentação");
    }

    @Test
    void approveWithCategorySetsColumnAndExtractedField() {
        UUID documentId = anExtractedDocumentWithUniqueSupplier("Cantina do Zé, Lda.");

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, "TesteDashboard09");

        assertThat(documents.findById(documentId).orElseThrow().getCategory()).isEqualTo("TesteDashboard09");

        ExtractedField categoryField = fields.findByDocumentIdAndFieldName(documentId, ExtractedFieldName.CATEGORY)
                .orElseThrow();
        assertThat(categoryField.getValueText()).isEqualTo("TesteDashboard09");
        assertThat(categoryField.getSource()).isEqualTo(FieldSource.HUMAN);
        assertThat(categoryField.getConfidence()).isNull();

        List<DocumentEvent> history = events.findByDocumentIdOrderByOccurredAtAscIdAsc(documentId);
        assertThat(history)
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo(DocumentEventType.FIELD_CORRECTED));
    }

    @Test
    void approveWithoutCategoryDoesNotTouchCategoryColumn() {
        UUID documentId = anExtractedDocumentWithUniqueSupplier("Cantina do Zé, Lda.");

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, null);

        assertThat(documents.findById(documentId).orElseThrow().getCategory()).isNull();
        assertThat(fields.findByDocumentIdAndFieldName(documentId, ExtractedFieldName.CATEGORY))
                .isNotPresent();
    }

    @Test
    void refusesToApproveADocumentFromAnotherOrganization() {
        UUID documentId = anExtractedDocument(null);
        UUID otherOrganizationId = UUID.randomUUID();

        assertThatThrownBy(() ->
                        approvals.approve(documentId, otherOrganizationId, Actor.system(), UserRole.FINANCE, null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void onlyAManagerOrAdminApprovesAboveTheOrganizationThreshold() {
        UUID documentId = anAboveThresholdDocument();

        assertThatThrownBy(() -> approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, null))
                .isInstanceOf(AccessDeniedException.class);

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.MANAGER, null);

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    }

    @Test
    void onlyAManagerOrAdminApprovesADocumentWithAHumanCorrectedAmount() {
        UUID documentId = anExtractedDocumentWithAHumanCorrectedTotal();

        assertThatThrownBy(() -> approvals.approve(documentId, organizationId, Actor.system(), UserRole.FINANCE, null))
                .as("o total foi corrigido à mão — mesmo abaixo do limite, só um gestor aprova")
                .isInstanceOf(AccessDeniedException.class);

        approvals.approve(documentId, organizationId, Actor.system(), UserRole.MANAGER, null);

        assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(DocumentStatus.APPROVED);
    }

    /** Abaixo do limite, mas com o total corrigido à mão — pede sempre um gestor. */
    private UUID anExtractedDocumentWithAHumanCorrectedTotal() {
        Document document = new Document(
                organizationId,
                submitterId,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                null,
                SUPPLIER_TAX_ID,
                "FT " + UUID.randomUUID(),
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("1.00")));
        ExtractedField.writtenByHuman(document, ExtractedFieldName.TOTAL_AMOUNT, "1.00");
        return documents.save(document).getId();
    }

    /** Acima do limite de 1000.00 EUR semeado por {@code DemoIdentityConfiguration}. */
    private UUID anAboveThresholdDocument() {
        Document document = new Document(
                organizationId,
                submitterId,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura-grande.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                null,
                SUPPLIER_TAX_ID,
                "FT 2026/2",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("2000.00"),
                new BigDecimal("460.00"),
                new BigDecimal("23.00"),
                new BigDecimal("2460.00")));
        return documents.save(document).getId();
    }

    /** Um documento já extraído, com NIF fixo e, opcionalmente, o nome do fornecedor. */
    private UUID anExtractedDocumentWithUniqueSupplier(String supplierName) {
        Document document = new Document(
                organizationId,
                submitterId,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        String uniqueTaxId = "9" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        document.projectInvoiceFields(new InvoiceFields(
                supplierName,
                uniqueTaxId,
                "FT 2026/1",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        document = documents.save(document);
        if (supplierName != null) {
            fields.save(ExtractedField.readByMachine(
                    document,
                    ExtractedFieldName.SUPPLIER_NAME,
                    supplierName,
                    new BigDecimal("0.95"),
                    new FieldGeometry(
                            1,
                            List.of(
                                    new FieldGeometry.Point(0.1, 0.1),
                                    new FieldGeometry.Point(0.4, 0.1),
                                    new FieldGeometry.Point(0.4, 0.2)))));
        }
        return document.getId();
    }

    /** Um documento já extraído, com NIF fixo e, opcionalmente, o nome do fornecedor. */
    private UUID anExtractedDocument(String supplierName) {
        Document document = new Document(
                organizationId,
                submitterId,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null);
        document.transitionTo(DocumentStatus.EXTRACTED, Actor.system(), null);
        document.projectInvoiceFields(new InvoiceFields(
                supplierName,
                SUPPLIER_TAX_ID,
                "FT 2026/1",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
        document = documents.save(document);
        if (supplierName != null) {
            fields.save(ExtractedField.readByMachine(
                    document,
                    ExtractedFieldName.SUPPLIER_NAME,
                    supplierName,
                    new BigDecimal("0.95"),
                    new FieldGeometry(
                            1,
                            List.of(
                                    new FieldGeometry.Point(0.1, 0.1),
                                    new FieldGeometry.Point(0.4, 0.1),
                                    new FieldGeometry.Point(0.4, 0.2)))));
        }
        return document.getId();
    }
}
