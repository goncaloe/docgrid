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
 * caminho) e o sítio da página onde foi lido — o que a interface de revisão sobrepõe ao
 * documento para dizer ao utilizador onde olhar.
 *
 * @param invoiceFields os valores tipados, prontos a guardar
 * @param confidences a confiança de cada campo lido, 0 a 1; um campo sem entrada é um
 *     campo cujo motor não declara confiança
 * @param geometries onde cada campo foi lido; tem de cobrir todos os campos de
 *     {@code confidences} — um campo com confiança tem sempre geometria
 */
public record ExtractionResult(
        InvoiceFields invoiceFields,
        Map<ExtractedFieldName, BigDecimal> confidences,
        Map<ExtractedFieldName, FieldGeometry> geometries) {

    public ExtractionResult {
        Objects.requireNonNull(invoiceFields, "invoiceFields");
        Objects.requireNonNull(confidences, "confidences");
        Objects.requireNonNull(geometries, "geometries");
        var read = Map.copyOf(confidences);
        var located = Map.copyOf(geometries);
        if (!located.keySet().containsAll(read.keySet())) {
            throw new IllegalArgumentException("Um campo com confiança tem de ter geometria: faltam "
                    + read.keySet().stream()
                            .filter(name -> !located.containsKey(name))
                            .toList());
        }
        confidences = read;
        geometries = located;
    }
}
