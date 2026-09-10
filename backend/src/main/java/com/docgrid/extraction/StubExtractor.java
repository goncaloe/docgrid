package com.docgrid.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;

/**
 * A extração que devolve sempre a mesma fatura, plausível e consistente.
 *
 * <p>É o que corre em local e nos testes: o pipeline inteiro — evento, fila, worker,
 * estados, idempotência, geometria — exercita-se a sério sem uma chamada à AWS. A etapa 04
 * substitui-a pelo Textract em AWS e por testes que não dependem desta classe; quem a
 * consome é a interface, e não sabe a diferença.
 *
 * <p>Os valores batem certo: a aritmética, a taxa de IVA e o NIF são válidos segundo as
 * regras que a etapa 05 ainda vai impor. Um stub que viola as regras de negócio obrigaria
 * os testes a distinguir entre "o pipeline está errado" e "os dados de exemplo estão".
 */
@Component
public class StubExtractor implements DocumentExtractor {

    private static final InvoiceFields INVOICE = new InvoiceFields(
            "Cantina do Zé, Lda.",
            "505123452",
            "FT 2026/123",
            LocalDate.of(2026, 9, 15),
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"));

    private static final Map<ExtractedFieldName, BigDecimal> CONFIDENCES = Map.of(
            ExtractedFieldName.SUPPLIER_NAME, new BigDecimal("0.94"),
            ExtractedFieldName.SUPPLIER_TAX_ID, new BigDecimal("0.97"),
            ExtractedFieldName.INVOICE_NUMBER, new BigDecimal("0.95"),
            ExtractedFieldName.ISSUE_DATE, new BigDecimal("0.98"),
            ExtractedFieldName.NET_AMOUNT, new BigDecimal("0.96"),
            ExtractedFieldName.VAT_AMOUNT, new BigDecimal("0.94"),
            ExtractedFieldName.VAT_RATE, new BigDecimal("0.99"),
            ExtractedFieldName.TOTAL_AMOUNT, new BigDecimal("0.97"));

    /** Sintética, só para cumprir a invariante até chegar a geometria a sério. */
    private static final Map<ExtractedFieldName, FieldGeometry> GEOMETRIES = CONFIDENCES.keySet().stream()
            .collect(Collectors.toMap(
                    name -> name,
                    name -> new FieldGeometry(
                            1,
                            List.of(
                                    new FieldGeometry.Point(0.1, 0.1),
                                    new FieldGeometry.Point(0.4, 0.1),
                                    new FieldGeometry.Point(0.4, 0.2)))));

    @Override
    public ExtractionResult extract(byte[] content, String contentType) {
        return new ExtractionResult(INVOICE, CONFIDENCES, GEOMETRIES);
    }
}
