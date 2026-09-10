package com.docgrid.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.docgrid.document.ExtractedFieldName;

/**
 * A fronteira entre o vocabulário do Textract e o nosso modelo: respostas reais gravadas
 * (em {@code src/test/resources/textract/}) têm de chegar ao outro lado como campos
 * tipados, com confiança e geometria, nos formatos que a validação (etapa 05) verifica.
 */
class TextractNormalizerTest {

    private static ExtractionResult normalize(String fixture) {
        return TextractNormalizer.fromResponse(TextractFixtures.load(fixture));
    }

    @Test
    void aCleanResponseBecomesEightTypedFieldsWithConfidenceAndGeometry() {
        ExtractionResult result = normalize("textract/clean-invoice.json");

        assertThat(result.invoiceFields().supplierName()).isEqualTo("Cantina do Zé, Lda.");
        assertThat(result.invoiceFields().supplierTaxId()).isEqualTo("505123452");
        assertThat(result.invoiceFields().invoiceNumber()).isEqualTo("FT 2026/123");
        assertThat(result.invoiceFields().issueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(result.invoiceFields().netAmount()).isEqualByComparingTo("100.00");
        assertThat(result.invoiceFields().vatAmount()).isEqualByComparingTo("23.00");
        assertThat(result.invoiceFields().vatRate()).isEqualByComparingTo("23.00");
        assertThat(result.invoiceFields().totalAmount()).isEqualByComparingTo("123.00");

        assertThat(result.confidences()).hasSize(8);
        assertThat(result.geometries()).hasSize(8);
        FieldGeometry geometry = result.geometries().get(ExtractedFieldName.TOTAL_AMOUNT);
        assertThat(geometry.polygon().get(0).x()).isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.001));
        assertThat(geometry.polygon().get(0).y()).isCloseTo(0.55, org.assertj.core.data.Offset.offset(0.001));
        assertThat(geometry.polygon()).hasSize(4);
    }

    @Test
    void amountsInEuropeanFormatArriveAsDecimalsWithScaleTwo() {
        ExtractionResult result = normalize("textract/comma-amounts.json");

        assertThat(result.invoiceFields().netAmount()).isEqualByComparingTo("1234.56");
        assertThat(result.invoiceFields().netAmount().scale()).isEqualTo(2);
        assertThat(result.invoiceFields().vatAmount()).isEqualByComparingTo("283.95");
        assertThat(result.invoiceFields().vatRate()).isEqualByComparingTo("23.00");
        assertThat(result.invoiceFields().totalAmount()).isEqualByComparingTo("1518.51");
    }

    @Test
    void datesInEveryDocumentedFormatNormalizeToIso() {
        assertThat(normalize("textract/date-formats.json").invoiceFields().issueDate())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(TextractNormalizer.normalizeDate("2026-09-15")).isEqualTo("2026-09-15");
        assertThat(TextractNormalizer.normalizeDate("15/09/2026")).isEqualTo("2026-09-15");
        assertThat(TextractNormalizer.normalizeDate("15-09-2026")).isEqualTo("2026-09-15");
        assertThat(TextractNormalizer.normalizeDate("15.09.2026")).isEqualTo("2026-09-15");
        assertThatThrownBy(() -> TextractNormalizer.normalizeDate("setembro de 2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("setembro");
    }

    @Test
    void amountsInEveryDocumentedFormatNormalizeByTheLastSeparator() {
        assertThat(TextractNormalizer.normalizeAmount("1.234,56")).isEqualTo("1234.56");
        assertThat(TextractNormalizer.normalizeAmount("1234.56")).isEqualTo("1234.56");
        assertThat(TextractNormalizer.normalizeAmount("€ 1 234,56")).isEqualTo("1234.56");
        assertThat(TextractNormalizer.normalizeAmount("1,234.56")).isEqualTo("1234.56");
        assertThat(TextractNormalizer.normalizeAmount("23%")).isEqualTo("23");
        assertThat(TextractNormalizer.normalizeAmount("100.00")).isEqualTo("100.00");
        assertThat(TextractNormalizer.normalizeAmount("23,50")).isEqualTo("23.50");
    }

    @Test
    void theHigherConfidenceCandidateWinsAndTheTieGoesToTheFirst() {
        ExtractionResult result = normalize("textract/duplicate-field.json");

        // TOTAL: o de confiança maior chega depois, e vence.
        assertThat(result.invoiceFields().totalAmount()).isEqualByComparingTo("123.00");
        // INVOICE_RECEIPT_ID: confianças iguais; fica o primeiro da resposta.
        assertThat(result.invoiceFields().invoiceNumber()).isEqualTo("FT 2026/1");
    }

    @Test
    void aFieldTheServiceDidNotReadStaysNullAndUntrusted() {
        ExtractionResult result = normalize("textract/missing-field.json");

        assertThat(result.invoiceFields().invoiceNumber()).isNull();
        assertThat(result.invoiceFields().issueDate()).isNull();
        assertThat(result.invoiceFields().netAmount()).isNull();
        assertThat(result.invoiceFields().vatAmount()).isNull();
        assertThat(result.confidences().keySet())
                .containsExactlyInAnyOrder(
                        ExtractedFieldName.SUPPLIER_NAME,
                        ExtractedFieldName.SUPPLIER_TAX_ID,
                        ExtractedFieldName.TOTAL_AMOUNT);
    }

    @Test
    void thePtPrefixAndSpacesComeOffTheTaxId() {
        assertThat(normalize("textract/pt-nif-prefix.json").invoiceFields().supplierTaxId())
                .isEqualTo("501442889");
        assertThat(TextractNormalizer.normalizeTaxId("PT 501442889")).isEqualTo("501442889");
        assertThat(TextractNormalizer.normalizeTaxId("PT501442889")).isEqualTo("501442889");
        assertThat(TextractNormalizer.normalizeTaxId("501 442 889")).isEqualTo("501442889");
        assertThat(TextractNormalizer.normalizeTaxId("501442889")).isEqualTo("501442889");
    }

    @Test
    void geometryArrivesInNormalizedCoordinatesAndThePageItSays() {
        // A resposta tem a data em três páginas; a de maior confiança é a da página 2,
        // e a geometria tem de chegar com a página que o Textract declarou.
        ExtractionResult result = normalize("textract/date-formats.json");

        FieldGeometry geometry = result.geometries().get(ExtractedFieldName.ISSUE_DATE);
        assertThat(geometry.page()).isEqualTo(2);
        assertThat(geometry.polygon()).allSatisfy(point -> {
            assertThat(point.x()).isBetween(0.0, 1.0);
            assertThat(point.y()).isBetween(0.0, 1.0);
        });
    }
}
