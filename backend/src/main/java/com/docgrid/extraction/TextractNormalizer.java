package com.docgrid.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.textract.model.AnalyzeExpenseResponse;
import software.amazon.awssdk.services.textract.model.ExpenseDetection;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.Geometry;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;

/**
 * O que o Textract respondeu → o nosso modelo de campos.
 *
 * <p>O Textract devolve campos tipados com o valor em texto, a confiança e a geometria já
 * normalizada; este normalizador é a única fronteira entre o vocabulário dele (e as
 * convenções de formatação de quem escreve faturas) e o resto do sistema. Datas em vários
 * formatos, montantes com vírgula ou ponto, NIF com ou sem prefixo {@code PT} — tudo
 * convergido aqui, para a validação (etapa 05) receber sempre o mesmo formato.
 *
 * <p>Só campos do sumário da fatura ({@code SummaryFields}) contam; os itens de linha
 * ficam para mais tarde. Um campo ausente fica a {@code null} e sem confiança — sem
 * heurísticas para adivinhar o que o serviço não leu: quem corrige é uma pessoa.
 *
 * <p>Dois candidatos para o mesmo campo: fica o de maior confiança; em empate, o primeiro
 * da resposta. Não há fusão de valores.
 */
final class TextractNormalizer {

    private static final Logger log = LoggerFactory.getLogger(TextractNormalizer.class);

    /**
     * Os tipos do sumário do {@code AnalyzeExpense} que nos interessam, e o campo em que
     * cada um se transforma. Um tipo que não está aqui é ignorado — o Textract devolve
     * muito mais do que precisamos.
     */
    private static final Map<String, ExtractedFieldName> TYPES = Map.ofEntries(
            Map.entry("VENDOR_NAME", ExtractedFieldName.SUPPLIER_NAME),
            Map.entry("VENDOR_TAX_ID", ExtractedFieldName.SUPPLIER_TAX_ID),
            Map.entry("INVOICE_RECEIPT_ID", ExtractedFieldName.INVOICE_NUMBER),
            Map.entry("INVOICE_RECEIPT_DATE", ExtractedFieldName.ISSUE_DATE),
            Map.entry("SUBTOTAL", ExtractedFieldName.NET_AMOUNT),
            Map.entry("TAX", ExtractedFieldName.VAT_AMOUNT),
            // A taxa só entra se o Textract a devolver como campo próprio; derivar
            // vat/net é inferência — trabalho da validação, não da extração.
            Map.entry("TAX_PCT", ExtractedFieldName.VAT_RATE),
            Map.entry("VAT_RATE", ExtractedFieldName.VAT_RATE),
            Map.entry("TOTAL", ExtractedFieldName.TOTAL_AMOUNT));

    private TextractNormalizer() {}

    static ExtractionResult fromResponse(AnalyzeExpenseResponse response) {
        Map<ExtractedFieldName, Candidate> best = new EnumMap<>(ExtractedFieldName.class);
        for (ExpenseDocument document : response.expenseDocuments()) {
            for (ExpenseField field : document.summaryFields()) {
                accept(best, field);
            }
        }

        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(ExtractedFieldName.class);
        Map<ExtractedFieldName, FieldGeometry> geometries = new EnumMap<>(ExtractedFieldName.class);
        var values = new String[TYPES.size()];
        for (var entry : TYPES.entrySet()) {
            Candidate candidate = best.get(entry.getValue());
            if (candidate == null) {
                continue;
            }
            confidences.put(entry.getValue(), candidate.confidence());
            geometries.put(entry.getValue(), candidate.geometry());
            values[entry.getValue().ordinal()] = switch (entry.getValue()) {
                case ISSUE_DATE -> normalizeDate(candidate.text());
                case NET_AMOUNT, VAT_AMOUNT, TOTAL_AMOUNT, VAT_RATE -> normalizeAmount(candidate.text());
                case SUPPLIER_TAX_ID -> normalizeTaxId(candidate.text());
                default -> candidate.text().trim();
            };
        }

        InvoiceFields fields = new InvoiceFields(
                values[ExtractedFieldName.SUPPLIER_NAME.ordinal()],
                values[ExtractedFieldName.SUPPLIER_TAX_ID.ordinal()],
                values[ExtractedFieldName.INVOICE_NUMBER.ordinal()],
                values[ExtractedFieldName.ISSUE_DATE.ordinal()] == null
                        ? null
                        : LocalDate.parse(values[ExtractedFieldName.ISSUE_DATE.ordinal()]),
                decimal(values[ExtractedFieldName.NET_AMOUNT.ordinal()]),
                decimal(values[ExtractedFieldName.VAT_AMOUNT.ordinal()]),
                decimal(values[ExtractedFieldName.VAT_RATE.ordinal()]),
                decimal(values[ExtractedFieldName.TOTAL_AMOUNT.ordinal()]));
        return new ExtractionResult(fields, confidences, geometries);
    }

    /**
     * Regista um candidato ao campo, se o tipo for um dos nossos e o valor existir. Fica
     * o de maior confiança; em empate, o primeiro — a ordem de iteração da resposta é a
     * ordem de desempate, e é estável.
     */
    private static void accept(Map<ExtractedFieldName, Candidate> best, ExpenseField field) {
        String type = field.type() == null ? null : field.type().text();
        if (type == null) {
            return;
        }
        ExtractedFieldName name = TYPES.get(type);
        if (name == null) {
            return;
        }
        ExpenseDetection detection = field.valueDetection();
        if (detection == null || detection.text() == null || detection.text().isBlank()) {
            log.info("Campo {} com valor vazio na resposta do Textract; fica sem valor", name);
            return;
        }
        Geometry geometry = detection.geometry();
        if (geometry == null || geometry.polygon() == null || geometry.polygon().size() < 3) {
            log.info("Campo {} sem geometria na resposta do Textract; fica sem valor", name);
            return;
        }
        var confidence = BigDecimal.valueOf(detection.confidence() == null ? 0f : detection.confidence());
        var geometryNormalized = new FieldGeometry(
                field.pageNumber() == null ? 1 : field.pageNumber(),
                geometry.polygon().stream()
                        .map(point -> new FieldGeometry.Point(point.x(), point.y()))
                        .toList());
        Candidate candidate = new Candidate(detection.text(), confidence, geometryNormalized);
        Candidate incumbent = best.get(name);
        if (incumbent == null || candidate.confidence().compareTo(incumbent.confidence()) > 0) {
            if (incumbent != null) {
                log.info(
                        "Campo {}: fica o candidato \"{}\" (confiança {}); descartado \"{}\" (confiança {})",
                        name,
                        candidate.text(),
                        candidate.confidence(),
                        incumbent.text(),
                        incumbent.confidence());
            }
            best.put(name, candidate);
        }
    }

    private record Candidate(String text, BigDecimal confidence, FieldGeometry geometry) {}

    /**
     * Datas em quatro formatos: ISO {@code yyyy-MM-dd} e os três de dia primeiro
     * ({@code dd/MM/yyyy}, {@code dd-MM-yyyy}, {@code dd.MM.yyyy}). O separador decide só
     * o caráter, não o significado: dia, mês, ano, por esta ordem, em todos menos no ISO.
     */
    static String normalizeDate(String raw) {
        String text = raw.trim();
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (DateTimeParseException ignored) {
            // não é ISO; segue para os formatos de dia primeiro
        }
        String[] parts = text.split("\\D+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Data num formato desconhecido: " + raw);
        }
        // Dia primeiro: dia, mês, ano → ISO ano-mês-dia.
        return "%s-%s-%s".formatted(parts[2], parts[1], parts[0]);
    }

    /**
     * Montantes com qualquer combinação de separadores: {@code "1.234,56"},
     * {@code "1234.56"}, {@code "€ 1 234,56"}, {@code "1,234.56"}. A regra é a do último
     * separador — o último {@code .} ou {@code ,} é o decimal, os anteriores são de
     * milhares —, que cobre as convenções europeia e anglo-saxónica sem configurar nada.
     */
    static String normalizeAmount(String raw) {
        String text = raw.replace("€", " ").replace("$", " ").replace("%", " ").trim();
        int decimalAt = Math.max(text.lastIndexOf('.'), text.lastIndexOf(','));
        if (decimalAt < 0) {
            return text.replace(" ", "");
        }
        String whole = text.substring(0, decimalAt);
        String fraction = text.substring(decimalAt + 1);
        // O que sobrou antes do decimal é milhares, em qualquer dos dois separadores.
        String digits = whole.replaceAll("[., ]", "");
        return digits + "." + fraction;
    }

    /** O NIF chega como vier: com ou sem prefixo {@code PT}, com ou sem espaços. */
    static String normalizeTaxId(String raw) {
        String text = raw.replaceAll("\\s", "").trim().toUpperCase();
        if (text.startsWith("PT")) {
            text = text.substring(2).trim();
        }
        return text;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
