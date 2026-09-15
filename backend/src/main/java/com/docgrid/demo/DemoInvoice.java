package com.docgrid.demo;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;
import com.docgrid.extraction.ExtractionResult;
import com.docgrid.extraction.FieldGeometry;

/**
 * Uma fatura de demonstração: os valores, a confiança com que a extração os "leu", onde
 * estão na página, e o caso de negócio que esta fatura existe para mostrar.
 *
 * <p>É a fonte única de cada documento semeado: o {@code InvoicePdfWriter} desenha o PDF a
 * partir daqui — cada campo na coordenada da sua geometria — e o {@code DemoExtractor}
 * devolve daqui o {@link ExtractionResult} quando o worker processa esse mesmo PDF. Os
 * dois lados não podem divergir, porque são o mesmo objeto.
 *
 * <p>Um campo que a extração não leu simplesmente não tem entrada em {@code confidences}
 * nem em {@code geometries}, e o valor correspondente em {@link InvoiceFields} é nulo —
 * a mesma convenção das fixtures do {@code StubExtractor}.
 *
 * @param slug o identificador que vai no marcador do PDF e o liga de volta ao catálogo
 * @param suggestedCategory a categoria habitual deste fornecedor, com que o seed aprova o
 *     documento; alimenta o histórico do fornecedor e o gráfico por categoria
 */
public record DemoInvoice(
        String slug,
        InvoiceFields fields,
        Map<ExtractedFieldName, BigDecimal> confidences,
        Map<ExtractedFieldName, FieldGeometry> geometries,
        String suggestedCategory,
        DemoCase demoCase) {

    public DemoInvoice {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(suggestedCategory, "suggestedCategory");
        Objects.requireNonNull(demoCase, "demoCase");
        confidences = Map.copyOf(confidences);
        geometries = Map.copyOf(geometries);
    }

    /** O que o {@code DemoExtractor} devolve ao worker para esta fatura. */
    public ExtractionResult asExtractionResult() {
        return new ExtractionResult(fields, confidences, geometries);
    }

    /** O valor deste campo como texto — a forma em que ele é guardado e corrigido. */
    public String textOf(ExtractedFieldName field) {
        return textOf(fields, field);
    }

    /**
     * O mesmo, para quem ainda não tem a fatura montada: o catálogo precisa disto para
     * saber que campos existem, e o seed para escrever a correção de um campo.
     */
    public static String textOf(InvoiceFields fields, ExtractedFieldName field) {
        return switch (field) {
            case SUPPLIER_NAME -> fields.supplierName();
            case SUPPLIER_TAX_ID -> fields.supplierTaxId();
            case INVOICE_NUMBER -> fields.invoiceNumber();
            case ISSUE_DATE ->
                fields.issueDate() == null ? null : fields.issueDate().toString();
            case NET_AMOUNT ->
                fields.netAmount() == null ? null : fields.netAmount().toPlainString();
            case VAT_AMOUNT ->
                fields.vatAmount() == null ? null : fields.vatAmount().toPlainString();
            case VAT_RATE -> fields.vatRate() == null ? null : fields.vatRate().toPlainString();
            case TOTAL_AMOUNT ->
                fields.totalAmount() == null ? null : fields.totalAmount().toPlainString();
            case CURRENCY, CATEGORY -> null;
        };
    }
}
