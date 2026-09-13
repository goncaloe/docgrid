package com.docgrid.export;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Escreve os dados de exportação como CSV UTF-8 com separador {@code ;}, fim de linha
 * {@code \r\n}, vírgula decimal, datas {@code dd-MM-yyyy} e quoting RFC 4180.
 *
 * <p>É um bean Spring para que o {@link ExportService} o possa injetar, mas mantém a
 * flexibilidade de ser usado diretamente nos testes que só querem gerar CSV sem Spring.
 */
@Component
class ExportCsvWriter {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final char SEPARATOR = ';';
    private static final String LINE_SEPARATOR = "\r\n";
    private static final String[] HEADER = {
        "Data", "Nº da fatura", "Fornecedor", "NIF",
        "Base tributável", "Taxa IVA", "IVA", "Total",
        "Categoria", "Moeda", "Ficheiro"
    };

    ExportCsvWriter() {}

    /**
     * Escreve as linhas como CSV em UTF-8, com BOM à cabeça.
     *
     * @param rows as linhas a exportar, nunca {@code null}
     * @return os bytes do CSV, BOM incluído
     */
    byte[] write(List<ExportRow> rows) {
        StringBuilder sb = new StringBuilder(rows.size() * 200);

        // cabeçalho
        for (int i = 0; i < HEADER.length; i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(HEADER[i]);
        }
        sb.append(LINE_SEPARATOR);

        // linhas de dados
        for (ExportRow row : rows) {
            appendCsvField(sb, row.issueDate().format(DATE_FORMATTER));
            sb.append(SEPARATOR);
            appendCsvField(sb, row.invoiceNumber());
            sb.append(SEPARATOR);
            appendCsvField(sb, row.supplierName());
            sb.append(SEPARATOR);
            appendCsvField(sb, row.supplierTaxId());
            sb.append(SEPARATOR);
            appendCsvField(sb, formatDecimal(row.netAmount()));
            sb.append(SEPARATOR);
            appendCsvField(sb, formatDecimal(row.vatRate()));
            sb.append(SEPARATOR);
            appendCsvField(sb, formatDecimal(row.vatAmount()));
            sb.append(SEPARATOR);
            appendCsvField(sb, formatDecimal(row.totalAmount()));
            sb.append(SEPARATOR);
            appendCsvField(sb, row.category());
            sb.append(SEPARATOR);
            appendCsvField(sb, row.currency());
            sb.append(SEPARATOR);
            appendCsvField(sb, row.originalFilename());
            sb.append(LINE_SEPARATOR);
        }

        return toByteArrayWithBom(sb);
    }

    /**
     * Appends a value to the StringBuilder, quoting and escaping per RFC 4180
     * when the value contains {@value #SEPARATOR}, {@literal "}, {@literal \r}, or {@literal \n}.
     */
    private static void appendCsvField(StringBuilder sb, String value) {
        if (value == null) {
            return;
        }
        boolean needsQuote = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;

        if (needsQuote) {
            sb.append('"');
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == '"') {
                    sb.append('"');
                }
                sb.append(ch);
            }
            sb.append('"');
        } else {
            sb.append(value);
        }
    }

    /**
     * Formata um {@code BigDecimal} sem separador de milhares e com vírgula decimal.
     * Por exemplo, {@code 1234.56} → {@code "1234,56"}.
     */
    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static byte[] toByteArrayWithBom(StringBuilder sb) {
        byte[] content = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[UTF_8_BOM.length + content.length];
        System.arraycopy(UTF_8_BOM, 0, result, 0, UTF_8_BOM.length);
        System.arraycopy(content, 0, result, UTF_8_BOM.length, content.length);
        return result;
    }
}
