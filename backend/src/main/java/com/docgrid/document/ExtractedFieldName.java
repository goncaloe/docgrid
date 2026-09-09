package com.docgrid.document;

/**
 * Os campos que se extraem de uma fatura, tal como listados em {@code docs/01-PRODUCT.md}.
 *
 * <p>Um enum e não texto livre: um campo com o nome mal escrito não é um campo novo, é um
 * erro, e a constraint {@code ck_extracted_fields_name} apanha-o na base de dados.
 */
public enum ExtractedFieldName {
    SUPPLIER_NAME,
    SUPPLIER_TAX_ID,
    INVOICE_NUMBER,
    ISSUE_DATE,
    NET_AMOUNT,
    VAT_AMOUNT,
    VAT_RATE,
    TOTAL_AMOUNT,
    CURRENCY,
    /** Sugerida a partir do histórico do fornecedor, nunca imposta. */
    CATEGORY
}
