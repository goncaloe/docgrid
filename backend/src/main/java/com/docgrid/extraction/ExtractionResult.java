package com.docgrid.extraction;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;

/**
 * O que a extração leu de um documento.
 *
 * <p>Os valores vêm tipados em {@link InvoiceFields} — é o formato em que se escrevem a
 * projeção e os campos extraídos — e cada um traz a confiança do motor (regra 6 do
 * {@code AGENTS.md}: a confiança acompanha o valor até à interface, sem se perder no
 * caminho).
 *
 * @param invoiceFields os valores tipados, prontos a guardar
 * @param confidences a confiança de cada campo lido, 0 a 1; um campo sem entrada é um
 *     campo cujo motor não declara confiança
 */
public record ExtractionResult(InvoiceFields invoiceFields, Map<ExtractedFieldName, BigDecimal> confidences) {

    public ExtractionResult {
        Objects.requireNonNull(invoiceFields, "invoiceFields");
        Objects.requireNonNull(confidences, "confidences");
        confidences = Map.copyOf(confidences);
    }
}
