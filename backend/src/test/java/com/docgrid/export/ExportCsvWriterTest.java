package com.docgrid.export;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExportCsvWriterTest {

    private final ExportCsvWriter writer = new ExportCsvWriter();

    /** Uma linha com o NIF, a moeda e o ficheiro fixos — só varia o que cada teste observa. */
    private static ExportRow row(
            LocalDate date,
            String invNum,
            String name,
            BigDecimal net,
            BigDecimal vatRate,
            BigDecimal vat,
            BigDecimal total,
            String category) {
        return new ExportRow(
                UUID.randomUUID(),
                date,
                invNum,
                "505123452",
                name,
                net,
                vatRate,
                vat,
                total,
                category,
                "EUR",
                "fatura.pdf");
    }

    @Test
    void startsWithUtf8Bom() {
        byte[] csv = writer.write(List.of());
        assertThat(csv).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void headerRowIsCorrect() {
        byte[] csv = writer.write(List.of());
        String content = new String(csv, UTF_8);
        // Sem o BOM, para comparar como texto
        String withoutBom = content.substring(1);
        assertThat(withoutBom)
                .startsWith(
                        "Data;Nº da fatura;Fornecedor;NIF;Base tributável;Taxa IVA;IVA;Total;Categoria;Moeda;Ficheiro\r\n");
    }

    @Test
    void formatsDateAsDdMmYyyy() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Fornecedor",
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00"),
                "Alimentação"));
        String csv = new String(writer.write(rows), UTF_8);
        assertThat(csv).contains("15-08-2026");
    }

    @Test
    void decimalCommaNotDot() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Fornecedor",
                new BigDecimal("1234.56"),
                new BigDecimal("23.00"),
                new BigDecimal("283.95"),
                new BigDecimal("1518.51"),
                "Alimentação"));
        String csv = new String(writer.write(rows), UTF_8);
        // 1234,56 e nunca 1234.56
        assertThat(csv).contains("1234,56");
        assertThat(csv).doesNotContain("1234.56");
    }

    @Test
    void nameWithCommaDoesNotNeedQuotes() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Cantina do Zé, Lda.",
                BigDecimal.TEN,
                new BigDecimal("23.00"),
                new BigDecimal("2.30"),
                new BigDecimal("12.30"),
                null));
        String csv = new String(writer.write(rows), UTF_8);

        String supplierField = csv.lines().skip(1).findFirst().orElse("");
        // A vírgula não é o separador deste CSV, e o valor não tem ; " \r nem \n:
        // pela RFC 4180 não leva aspas.
        assertThat(supplierField).doesNotContain("\"Cantina do Zé, Lda.\"");
    }

    @Test
    void nameWithSemicolonIsQuoted() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Cantina; do Zé",
                BigDecimal.TEN,
                new BigDecimal("23.00"),
                new BigDecimal("2.30"),
                new BigDecimal("12.30"),
                null));
        String csv = new String(writer.write(rows), UTF_8);
        assertThat(csv).contains("\"Cantina; do Zé\"");
    }

    @Test
    void nameWithDoubleQuoteIsEscaped() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Fornecedor \"XPTO\"",
                BigDecimal.TEN,
                new BigDecimal("23.00"),
                new BigDecimal("2.30"),
                new BigDecimal("12.30"),
                null));
        String csv = new String(writer.write(rows), UTF_8);
        assertThat(csv).contains("\"Fornecedor \"\"XPTO\"\"\"");
    }

    @Test
    void accentsSurviveEncoding() {
        var rows = List.of(row(
                LocalDate.of(2026, 8, 15),
                "FT 1",
                "Cantina do Zé, Lda.",
                BigDecimal.TEN,
                new BigDecimal("23.00"),
                new BigDecimal("2.30"),
                new BigDecimal("12.30"),
                "Alimentação"));
        byte[] csv = writer.write(rows);
        String content = new String(csv, UTF_8);
        assertThat(content).contains("Alimentação");
        assertThat(content).contains("Zé");
    }
}
