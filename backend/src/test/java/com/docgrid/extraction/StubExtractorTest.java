package com.docgrid.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.docgrid.document.ExtractedFieldName;

/**
 * O stub devolve exatamente o que a fixture diz — nem mais, nem melhor. O caso que se
 * escolhe em {@code docgrid.extraction.stub-fixture} tem de chegar intacto a quem
 * consome {@link DocumentExtractor}, senão os testes de pipeline mentem sobre o caso.
 */
class StubExtractorTest {

    private static final String CLEAN = "extraction/fixtures/clean-invoice.json";
    private static final String LOW_CONFIDENCE = "extraction/fixtures/low-confidence.json";
    private static final String MISSING_FIELD = "extraction/fixtures/missing-field.json";

    private static StubExtractor stub(String fixturePath) {
        return new StubExtractor(new ExtractionProperties(
                fixturePath, new ExtractionProperties.Textract("eu-west-1", "", "", "", Duration.ofSeconds(60))));
    }

    @Test
    void theCleanFixtureYieldsEightFieldsWithHighConfidenceAndGeometry() {
        ExtractionResult result = stub(CLEAN).extract(new byte[0], "application/pdf");

        assertThat(result.confidences()).hasSize(8);
        assertThat(result.confidences()).allSatisfy((field, confidence) -> {
            assertThat(confidence).isGreaterThanOrEqualTo(new BigDecimal("0.85"));
            assertThat(result.geometries()).containsKey(field);
        });
        assertThat(result.geometries()).hasSize(8);
    }

    @Test
    void theShakyPhotoFixtureReadsEveryFieldButWithLowConfidence() {
        ExtractionResult result = stub(LOW_CONFIDENCE).extract(new byte[0], "application/pdf");

        assertThat(result.confidences()).hasSize(8);
        assertThat(result.confidences())
                .as("uma foto tremida não produz um campo em que se confie")
                .allSatisfy((field, confidence) -> assertThat(confidence).isLessThanOrEqualTo(new BigDecimal("0.80")));
    }

    @Test
    void theMissingFieldFixtureLeavesTheUnreadFieldsOutOfTheResult() {
        ExtractionResult result = stub(MISSING_FIELD).extract(new byte[0], "application/pdf");

        assertThat(result.confidences()).hasSize(5);
        assertThat(result.invoiceFields().invoiceNumber()).isNull();
        assertThat(result.invoiceFields().vatAmount()).isNull();
        assertThat(Set.copyOf(result.confidences().keySet()))
                .doesNotContain(ExtractedFieldName.INVOICE_NUMBER, ExtractedFieldName.VAT_AMOUNT);
    }

    @Test
    void amountsArriveAsDecimalsWithScaleTwo() {
        ExtractionResult result = stub(CLEAN).extract(new byte[0], "application/pdf");

        assertThat(result.invoiceFields().netAmount()).isEqualByComparingTo("100.00");
        assertThat(result.invoiceFields().netAmount().scale()).isEqualTo(2);
        assertThat(result.invoiceFields().vatAmount()).isEqualByComparingTo("23.00");
        assertThat(result.invoiceFields().totalAmount()).isEqualByComparingTo("123.00");
        assertThat(result.invoiceFields().vatRate()).isEqualByComparingTo("23.00");
    }

    @Test
    void supplierNameIsReadLikeEveryOtherField() {
        ExtractionResult result = stub(CLEAN).extract(new byte[0], "application/pdf");

        assertThat(result.invoiceFields().supplierName()).isEqualTo("Cantina do Zé, Lda.");
        assertThat(result.confidences().get(ExtractedFieldName.SUPPLIER_NAME)).isNotNull();
        assertThat(result.geometries().get(ExtractedFieldName.SUPPLIER_NAME).page())
                .isEqualTo(1);
    }

    @Test
    void aMissingFixtureFailsLoudlyAtConstruction() {
        assertThatThrownBy(() -> stub("extraction/fixtures/nao-existe.json")).hasMessageContaining("nao-existe.json");
    }
}
