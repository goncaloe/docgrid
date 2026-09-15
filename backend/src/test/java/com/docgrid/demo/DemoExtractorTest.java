package com.docgrid.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.docgrid.extraction.ExtractionResult;

/**
 * O PDF vai para o S3 e volta pelo worker: entre a escrita e a leitura passa a fila
 * inteira. Se o marcador se perder pelo caminho, as sessenta faturas voltam a ser a mesma
 * — que é exatamente o problema que este perfil existe para resolver.
 */
class DemoExtractorTest {

    private final DemoInvoiceCatalog catalog = new DemoInvoiceCatalog(60, LocalDate.of(2026, 9, 15));
    private final DemoExtractor extractor = new DemoExtractor(catalog);

    @Test
    @DisplayName("o que se escreveu no PDF é o que se lê de volta")
    void roundTripsEveryInvoice() {
        assertThat(catalog.invoices()).allSatisfy(invoice -> {
            ExtractionResult result = extractor.extract(InvoicePdfWriter.write(invoice), "application/pdf");

            assertThat(result.invoiceFields()).isEqualTo(invoice.fields());
            assertThat(result.confidences()).isEqualTo(invoice.confidences());
            assertThat(result.geometries()).isEqualTo(invoice.geometries());
        });
    }

    @Test
    @DisplayName("um PDF de fora, sem marcador, recebe a primeira fatura em vez de falhar")
    void fallsBackToTheFirstInvoice() {
        ExtractionResult result = extractor.extract("%PDF-1.4\num ficheiro qualquer".getBytes(), "application/pdf");

        assertThat(result.invoiceFields()).isEqualTo(catalog.first().fields());
    }

    @Test
    @DisplayName("um marcador que não está no catálogo também não faz o worker falhar")
    void unknownMarkerFallsBack() {
        byte[] pdf = ("%PDF-1.4\n" + InvoicePdfWriter.MARKER_PREFIX + "inv-999\n").getBytes();

        assertThat(extractor.extract(pdf, "application/pdf").invoiceFields())
                .isEqualTo(catalog.first().fields());
    }

    @Test
    @DisplayName("o health check diz qual é o motor e quantas faturas tem")
    void reportsItsStatus() {
        assertThat(extractor.status().engine()).isEqualTo("demo");
        assertThat(extractor.status().ready()).isTrue();
        assertThat(extractor.status().detail()).contains("60 faturas");
    }
}
