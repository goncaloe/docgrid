package com.docgrid.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.extraction.FieldGeometry;
import com.docgrid.support.RepositoryTest;

/**
 * O critério de aceitação da etapa: um campo extraído guarda valor, confiança, origem e —
 * desde a etapa 04 — o sítio da página onde foi lido, e guarda tudo até ao outro lado da
 * base de dados. As constraints de {@code extracted_fields} são o modelo em SQL, e os
 * casos que as violam falham aqui, não em produção.
 */
@RepositoryTest
class ExtractedFieldRepositoryTest {

    @Autowired
    private ExtractedFieldRepository fields;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Document document;

    @BeforeEach
    void setUp() {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID submitterId = AuthFixtures.user(entityManager, organizationId);
        document =
                new Document(organizationId, submitterId, "org/a/2026/09/fatura.pdf", "fatura.pdf", "application/pdf");
        entityManager.persistAndFlush(document);
    }

    private static FieldGeometry geometry() {
        return new FieldGeometry(
                1,
                List.of(
                        new FieldGeometry.Point(0.1, 0.1),
                        new FieldGeometry.Point(0.4, 0.1),
                        new FieldGeometry.Point(0.4, 0.2)));
    }

    @Test
    void keepsValueConfidenceSourceAndGeometryOfAFieldReadByTheMachine() {
        ExtractedField.readByMachine(
                document, ExtractedFieldName.SUPPLIER_TAX_ID, "501442889", new BigDecimal("0.876"), geometry());
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.SUPPLIER_TAX_ID))
                .get()
                .satisfies(field -> {
                    assertThat(field.getValueText()).isEqualTo("501442889");
                    assertThat(field.getConfidence()).isEqualByComparingTo("0.876");
                    assertThat(field.getSource()).isEqualTo(FieldSource.AI);
                    assertThat(field.getPage()).isEqualTo(1);
                    assertThat(field.getBoundingBox()).isEqualTo("[[0.1, 0.1], [0.4, 0.1], [0.4, 0.2]]");
                });
    }

    @Test
    void keepsAFieldWrittenByAPersonWithoutInventingAConfidenceOrGeometry() {
        ExtractedField.writtenByHuman(document, ExtractedFieldName.CATEGORY, "Deslocações");
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.CATEGORY))
                .get()
                .satisfies(field -> {
                    assertThat(field.getValueText()).isEqualTo("Deslocações");
                    assertThat(field.getSource()).isEqualTo(FieldSource.HUMAN);
                    assertThat(field.getConfidence()).isNull();
                    assertThat(field.getPage()).isNull();
                    assertThat(field.getBoundingBox()).isNull();
                });
    }

    @Test
    void readsEveryFieldOfADocument() {
        ExtractedField.readByMachine(
                document, ExtractedFieldName.NET_AMOUNT, "100.00", new BigDecimal("0.990"), geometry());
        ExtractedField.readByMachine(
                document, ExtractedFieldName.VAT_AMOUNT, "23.00", new BigDecimal("0.980"), geometry());
        ExtractedField.readByMachine(
                document, ExtractedFieldName.TOTAL_AMOUNT, "123.00", new BigDecimal("0.995"), geometry());
        entityManager.flush();
        entityManager.clear();

        assertThat(fields.findByDocumentId(document.getId()))
                .extracting(ExtractedField::getFieldName)
                .containsExactlyInAnyOrder(
                        ExtractedFieldName.NET_AMOUNT, ExtractedFieldName.VAT_AMOUNT, ExtractedFieldName.TOTAL_AMOUNT);
    }

    @Test
    void refusesTwoValuesForTheSameFieldOfTheSameDocument() {
        ExtractedField.readByMachine(
                document, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/1", new BigDecimal("0.9"), geometry());
        entityManager.flush();

        ExtractedField.readByMachine(
                document, ExtractedFieldName.INVOICE_NUMBER, "FT 2026/2", new BigDecimal("0.8"), geometry());

        assertThatThrownBy(fields::flush)
                .as("um campo tem um valor corrente; o anterior fica em document_events")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void turnsAMachineFieldIntoAHumanOneWhenCorrected() {
        ExtractedField field = ExtractedField.readByMachine(
                document, ExtractedFieldName.TOTAL_AMOUNT, "123.00", new BigDecimal("0.410"), geometry());
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
                    assertThat(corrected.getPage())
                            .as("quem corrigiu escreveu um valor novo, sem coordenadas")
                            .isNull();
                    assertThat(corrected.getBoundingBox()).isNull();
                });
    }

    @Test
    void theDatabaseRefusesAMachineFieldWithoutGeometry() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into extracted_fields (id, document_id, field_name, value_text, confidence, source,"
                                + " created_at, updated_at) values (?, ?, ?, null, 0.5, 'AI', now(), now())",
                        UUID.randomUUID(),
                        document.getId(),
                        "INVOICE_NUMBER"))
                .as("a constraint ck_extracted_fields_bbox_source em SQL: AI traz sempre geometria")
                .hasMessageContaining("ck_extracted_fields_bbox_source");
    }

    @Test
    void theDatabaseRefusesAHumanFieldWithGeometry() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into extracted_fields (id, document_id, field_name, value_text, source, page,"
                                + " bounding_box, created_at, updated_at) values (?, ?, ?, 'X', 'HUMAN', 1,"
                                + " '[[0.1,0.1],[0.4,0.1],[0.4,0.2]]'::jsonb, now(), now())",
                        UUID.randomUUID(),
                        document.getId(),
                        "CATEGORY"))
                .as("a constraint ck_extracted_fields_bbox_source em SQL: HUMAN sem geometria")
                .hasMessageContaining("ck_extracted_fields_bbox_source");
    }

    @Test
    void theDatabaseRefusesABoundingBoxThatIsNotAnArray() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into extracted_fields (id, document_id, field_name, value_text, confidence, source,"
                                + " page, bounding_box, created_at, updated_at) values (?, ?, ?, null, 0.5, 'AI',"
                                + " 1, '{\"x\":0.1}'::jsonb, now(), now())",
                        UUID.randomUUID(),
                        document.getId(),
                        "INVOICE_NUMBER"))
                .as("a constraint ck_extracted_fields_bbox_json em SQL: o bounding box é um array de pontos")
                .hasMessageContaining("ck_extracted_fields_bbox_json");
    }
}
