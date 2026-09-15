package com.docgrid.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.docgrid.document.ExtractedFieldName;

/**
 * O PDF é escrito à mão, byte a byte: se a tabela de referências cruzadas ficar a apontar
 * para o sítio errado, nenhum leitor abre o ficheiro — e isso descobre-se no ecrã de
 * revisão, com o painel esquerdo em branco, que é o pior sítio para descobrir.
 */
class InvoicePdfWriterTest {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private final DemoInvoiceCatalog catalog = new DemoInvoiceCatalog(60, LocalDate.of(2026, 9, 15));

    @Test
    @DisplayName("é um PDF: cabeçalho, marcador e fim de ficheiro")
    void writesAPdf() {
        byte[] pdf = InvoicePdfWriter.write(catalog.first());

        String text = new String(pdf, WIN_ANSI);
        assertThat(text).startsWith("%PDF-1.4\n");
        assertThat(text.lines().toList().get(1)).isEqualTo("%DocGridDemo: inv-001");
        assertThat(text).endsWith("%%EOF\n");
    }

    @Test
    @DisplayName("o marcador volta a ler-se do ficheiro escrito")
    void markerRoundTrips() {
        assertThat(catalog.invoices())
                .allSatisfy(invoice -> assertThat(InvoicePdfWriter.markerOf(InvoicePdfWriter.write(invoice)))
                        .contains(invoice.slug()));
    }

    @Test
    @DisplayName("um ficheiro sem marcador não inventa um slug")
    void ignoresFilesWithoutMarker() {
        assertThat(InvoicePdfWriter.markerOf("%PDF-1.4\nsem marcador nenhum\n".getBytes(WIN_ANSI)))
                .isEmpty();
        assertThat(InvoicePdfWriter.markerOf(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("faturas diferentes dão ficheiros diferentes: a deteção de duplicado binário não dispara em todas")
    void differentInvoicesProduceDifferentBytes() {
        List<String> hashes = catalog.invoices().stream()
                .map(invoice -> sha256(InvoicePdfWriter.write(invoice)))
                .toList();

        assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a mesma fatura dá sempre o mesmo ficheiro")
    void isDeterministic() {
        DemoInvoice invoice = catalog.first();

        assertThat(InvoicePdfWriter.write(invoice)).isEqualTo(InvoicePdfWriter.write(invoice));
    }

    @Test
    @DisplayName("a tabela de referências aponta para onde o startxref promete")
    void crossReferenceTableIsConsistent() {
        byte[] pdf = InvoicePdfWriter.write(catalog.first());

        String text = new String(pdf, WIN_ANSI);
        int startXref = Integer.parseInt(
                text.substring(text.lastIndexOf("startxref\n") + "startxref\n".length(), text.lastIndexOf("\n%%EOF"))
                        .trim());
        assertThat(text.substring(startXref)).startsWith("xref\n0 7\n");

        // A primeira entrada depois da livre é o objeto 1: tem de cair sobre "1 0 obj".
        int firstObject = Integer.parseInt(text.substring(startXref + "xref\n0 7\n0000000000 65535 f \n".length())
                .substring(0, 10));
        assertThat(text.substring(firstObject)).startsWith("1 0 obj");
    }

    @Test
    @DisplayName("os valores da fatura aparecem no ficheiro, com os acentos do fornecedor")
    void drawsTheInvoiceValues() {
        DemoInvoice invoice = catalog.invoices().stream()
                .filter(candidate -> candidate.fields().supplierName().contains("ç"))
                .findFirst()
                .orElseThrow();

        String text = new String(InvoicePdfWriter.write(invoice), WIN_ANSI);
        assertThat(text).contains(invoice.fields().supplierName());
        assertThat(text).contains(invoice.fields().supplierTaxId());
        assertThat(text).contains(invoice.fields().invoiceNumber());
    }

    @Test
    @DisplayName("um campo que a extração não leu não é desenhado")
    void doesNotDrawMissingFields() {
        DemoInvoice missingDate = catalog.invoices().stream()
                .filter(invoice -> invoice.demoCase() == DemoCase.MISSING_FIELD)
                .filter(invoice -> invoice.fields().issueDate() == null)
                .findFirst()
                .orElseThrow();

        String text = new String(InvoicePdfWriter.write(missingDate), WIN_ANSI);
        assertThat(missingDate.geometries()).doesNotContainKey(ExtractedFieldName.ISSUE_DATE);
        assertThat(text).contains("(Data) Tj"); // o rótulo fica; é o valor que falta
        assertThat(text).doesNotContain("/2026) Tj");
    }

    @Test
    @DisplayName("o montante sai escrito como numa fatura portuguesa")
    void formatsAmountsInPortuguese() {
        DemoInvoice expensive = catalog.invoices().stream()
                .filter(invoice -> invoice.fields().totalAmount().compareTo(new BigDecimal("1000")) > 0)
                .findFirst()
                .orElseThrow();

        String text = new String(InvoicePdfWriter.write(expensive), WIN_ANSI);
        assertThat(text).containsPattern("\\(\\d\\.\\d{3},\\d{2} .\\) Tj");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
