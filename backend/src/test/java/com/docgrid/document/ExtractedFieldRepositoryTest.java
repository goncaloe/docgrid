package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.support.RepositoryTest;

/**
 * O critério de aceitação da etapa: um campo extraído guarda valor, confiança e origem, e
 * guarda-os até ao outro lado da base de dados.
 */
@RepositoryTest
class ExtractedFieldRepositoryTest {

    @Autowired
    private ExtractedFieldRepository fields;

    @Autowired
    private TestEntityManager entityManager;

    private Document document;

    @BeforeEach
    void setUp() {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID submitterId = AuthFixtures.user(entityManager, organizationId);
        document =
                new Document(organizationId, submitterId, "org/a/2026/09/fatura.pdf", "fatura.pdf", "application/pdf");
        entityManager.persistAndFlush(document);
    }

    @Test
    void keepsValueConfidenceAndSourceOfAFieldReadByTheMachine() {
        ExtractedField.readByMachine(
                document, ExtractedFieldName.SUPPLIER_TAX_ID, "501442889", new BigDecimal("0.876"));
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.SUPPLIER_TAX_ID))
                .get()
                .satisfies(field -> {
                    assertThat(field.getValueText()).isEqualTo("501442889");
                    assertThat(field.getConfidence()).isEqualByComparingTo("0.876");
                    assertThat(field.getSource()).isEqualTo(FieldSource.AI);
                });
    }

    @Test
    void keepsAFieldWrittenByAPersonWithoutInventingAConfidence() {
        ExtractedField.writtenByHuman(document, ExtractedFieldName.CATEGORY, "Deslocações");
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.CATEGORY))
                .get()
                .satisfies(field -> {
                    assertThat(field.getValueText()).isEqualTo("Deslocações");
                    assertThat(field.getSource()).isEqualTo(FieldSource.HUMAN);
                    assertThat(field.getConfidence()).isNull();
                });
    }

    @Test
    void readsEveryFieldOfADocument() {
        ExtractedField.readByMachine(document, ExtractedFieldName.NET_AMOUNT, "100.00", new BigDecimal("0.990"));
        ExtractedField.readByMachine(document, ExtractedFieldName.VAT_AMOUNT, "23.00", new BigDecimal("0.980"));
        ExtractedField.readByMachine(document, ExtractedFieldName.TOTAL_AMOUNT, "123.00", new BigDecimal("0.995"));
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentId(document.getId()))
                .extracting(ExtractedField::getFieldName)
                .containsExactlyInAnyOrder(
                        ExtractedFieldName.NET_AMOUNT, ExtractedFieldName.VAT_AMOUNT, ExtractedFieldName.TOTAL_AMOUNT);
    }

    @Test
    void refusesTwoValuesForTheSameFieldOfTheSameDocument() {
        ExtractedField.readByMachine(document, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/1", new BigDecimal("0.9"));
        entityManager.flush();

        ExtractedField.readByMachine(document, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/2", new BigDecimal("0.8"));

        assertThatThrownBy(fields::flush)
                .as("um campo tem um valor corrente; o anterior fica em document_events")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void turnsAMachineFieldIntoAHumanOneWhenCorrected() {
        ExtractedField field = ExtractedField.readByMachine(
                document, ExtractedFieldName.TOTAL_AMOUNT, "123.00", new BigDecimal("0.410"));
        entityManager.flush();

        field.correctTo("132.00");
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.TOTAL_AMOUNT))
                .get()
                .satisfies(corrected -> {
                    assertThat(corrected.getValueText()).isEqualTo("132.00");
                    assertThat(corrected.getSource()).isEqualTo(FieldSource.HUMAN);
                    assertThat(corrected.getConfidence()).isNull();
                });
    }
}
