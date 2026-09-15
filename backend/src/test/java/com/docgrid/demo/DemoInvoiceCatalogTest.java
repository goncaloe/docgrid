package com.docgrid.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;

/**
 * O catálogo é a fonte de toda a demonstração: se ele varia entre execuções, ou se um
 * caso plantado desaparece, o GIF e a demonstração ao vivo deixam de mostrar o que
 * prometem — e ninguém dá por isso até estar a gravar.
 */
class DemoInvoiceCatalogTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    private final DemoInvoiceCatalog catalog = new DemoInvoiceCatalog(60, TODAY);

    @Test
    @DisplayName("gera 60 faturas com slugs únicos")
    void generatesSixtyInvoices() {
        assertThat(catalog.invoices()).hasSize(60);
        assertThat(catalog.invoices()).extracting(DemoInvoice::slug).doesNotHaveDuplicates();
        assertThat(catalog.findBySlug("inv-001")).contains(catalog.first());
        assertThat(catalog.findBySlug("nao-existe")).isEmpty();
    }

    @Test
    @DisplayName("duas invocações produzem exatamente o mesmo catálogo")
    void isDeterministic() {
        DemoInvoiceCatalog again = new DemoInvoiceCatalog(60, TODAY);

        assertThat(again.invoices()).isEqualTo(catalog.invoices());
    }

    @Test
    @DisplayName("todos os NIFs têm dígito de controlo válido, menos os dois plantados como inválidos")
    void taxIdsHaveValidCheckDigit() {
        List<DemoInvoice> invalid = casesOf(DemoCase.INVALID_TAX_ID);
        assertThat(invalid).hasSize(2);
        assertThat(invalid)
                .allSatisfy(invoice ->
                        assertThat(hasValidCheckDigit(taxIdOf(invoice))).isFalse());

        assertThat(catalog.invoices())
                .filteredOn(Predicate.not(invalid::contains))
                .allSatisfy(invoice ->
                        assertThat(hasValidCheckDigit(taxIdOf(invoice))).isTrue());
    }

    @Test
    @DisplayName("oito faturas trazem um campo abaixo do limiar de confiança")
    void plantsLowConfidenceInvoices() {
        List<DemoInvoice> lowConfidence = casesOf(DemoCase.LOW_CONFIDENCE);

        assertThat(lowConfidence).hasSize(8);
        assertThat(lowConfidence)
                .allSatisfy(invoice -> assertThat(invoice.confidences().values())
                        .anySatisfy(confidence -> assertThat(confidence).isLessThan(new BigDecimal("0.85"))));
    }

    @Test
    @DisplayName("três faturas têm a soma errada e as restantes batem certo")
    void plantsBadArithmeticInvoices() {
        List<DemoInvoice> badArithmetic = casesOf(DemoCase.BAD_ARITHMETIC);

        assertThat(badArithmetic).hasSize(3);
        assertThat(badArithmetic)
                .allSatisfy(
                        invoice -> assertThat(sumMatchesTotal(invoice.fields())).isFalse());
        assertThat(catalog.invoices())
                .filteredOn(Predicate.not(badArithmetic::contains))
                .allSatisfy(
                        invoice -> assertThat(sumMatchesTotal(invoice.fields())).isTrue());
    }

    @Test
    @DisplayName("duas faturas perdem um campo obrigatório, sem valor nem confiança nem geometria")
    void plantsMissingFieldInvoices() {
        List<DemoInvoice> missing = casesOf(DemoCase.MISSING_FIELD);

        assertThat(missing).hasSize(2);
        assertThat(missing).anySatisfy(invoice -> {
            assertThat(invoice.fields().issueDate()).isNull();
            assertThat(invoice.confidences()).doesNotContainKey(ExtractedFieldName.ISSUE_DATE);
            assertThat(invoice.geometries()).doesNotContainKey(ExtractedFieldName.ISSUE_DATE);
        });
        assertThat(missing).anySatisfy(invoice -> {
            assertThat(invoice.fields().invoiceNumber()).isNull();
            assertThat(invoice.confidences()).doesNotContainKey(ExtractedFieldName.INVOICE_NUMBER);
            assertThat(invoice.geometries()).doesNotContainKey(ExtractedFieldName.INVOICE_NUMBER);
        });
    }

    @Test
    @DisplayName("há um par com o mesmo NIF e o mesmo número de fatura")
    void plantsOneDuplicatePair() {
        DemoInvoice original = onlyCaseOf(DemoCase.DUPLICATE_ORIGINAL);
        DemoInvoice copy = onlyCaseOf(DemoCase.DUPLICATE_COPY);

        assertThat(copy.fields().supplierTaxId()).isEqualTo(original.fields().supplierTaxId());
        assertThat(copy.fields().invoiceNumber()).isEqualTo(original.fields().invoiceNumber());
        assertThat(copy.fields().totalAmount()).isEqualTo(original.fields().totalAmount());
        assertThat(copy.slug()).isNotEqualTo(original.slug());
    }

    @Test
    @DisplayName("cada campo lido tem confiança e geometria, e nenhum campo em falta as tem")
    void readFieldsCarryConfidenceAndGeometry() {
        assertThat(catalog.invoices()).allSatisfy(invoice -> {
            assertThat(invoice.geometries().keySet())
                    .isEqualTo(invoice.confidences().keySet());
            // O construtor do ExtractionResult impõe a mesma regra; aqui falha mais cedo.
            assertThat(invoice.asExtractionResult().confidences()).isEqualTo(invoice.confidences());
        });
    }

    @Test
    @DisplayName("as datas de emissão caem nos últimos seis meses")
    void issueDatesAreRecent() {
        assertThat(catalog.invoices())
                .map(DemoInvoice::fields)
                .map(InvoiceFields::issueDate)
                .filteredOn(java.util.Objects::nonNull)
                .allSatisfy(issueDate -> assertThat(issueDate).isBetween(TODAY.minusMonths(6), TODAY));
    }

    @Test
    @DisplayName("a taxa de IVA é sempre uma das portuguesas")
    void vatRatesArePortuguese() {
        assertThat(catalog.invoices())
                .map(invoice -> invoice.fields().vatRate())
                .allSatisfy(rate -> assertThat(rate)
                        .isIn(new BigDecimal("6.00"), new BigDecimal("13.00"), new BigDecimal("23.00")));
    }

    @Test
    @DisplayName("um catálogo pequeno continua válido: o teste do seed corre com quatro faturas")
    void supportsSmallCatalogs() {
        DemoInvoiceCatalog small = new DemoInvoiceCatalog(4, TODAY);

        assertThat(small.invoices()).hasSize(4);
        assertThat(small.invoices())
                .extracting(DemoInvoice::slug)
                .containsExactly("inv-001", "inv-002", "inv-003", "inv-004");
    }

    private List<DemoInvoice> casesOf(DemoCase demoCase) {
        return catalog.invoices().stream()
                .filter(invoice -> invoice.demoCase() == demoCase)
                .toList();
    }

    private DemoInvoice onlyCaseOf(DemoCase demoCase) {
        List<DemoInvoice> found = casesOf(demoCase);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static String taxIdOf(DemoInvoice invoice) {
        return invoice.fields().supplierTaxId();
    }

    private static boolean sumMatchesTotal(InvoiceFields fields) {
        return fields.netAmount().add(fields.vatAmount()).compareTo(fields.totalAmount()) == 0;
    }

    /**
     * O módulo 11 do NIF português, escrito aqui a partir da especificação: a
     * {@code TaxIdRule} que o valida a sério é interna ao pacote da validação, e um teste
     * que chamasse o próprio gerador não provava nada.
     */
    private static boolean hasValidCheckDigit(String taxId) {
        if (taxId == null || !taxId.matches("[0-9]{9}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (taxId.charAt(i) - '0') * (9 - i);
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return expected == taxId.charAt(8) - '0';
    }

    /** Guarda-rede: quem receber uma fatura do catálogo não lhe consegue mexer. */
    @Test
    @DisplayName("as confianças e as geometrias são imutáveis")
    void mapsAreImmutable() {
        Map<ExtractedFieldName, BigDecimal> confidences = catalog.first().confidences();

        assertThatThrownBy(() -> confidences.put(ExtractedFieldName.CURRENCY, BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.invoices().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
