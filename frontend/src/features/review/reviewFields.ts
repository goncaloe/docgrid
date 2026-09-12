import type { ExtractedFieldName } from "../../api/types";

/** A ordem em que os campos aparecem no formulário de revisão. */
export const FIELD_ORDER: ExtractedFieldName[] = [
  "SUPPLIER_NAME",
  "SUPPLIER_TAX_ID",
  "INVOICE_NUMBER",
  "ISSUE_DATE",
  "NET_AMOUNT",
  "VAT_AMOUNT",
  "VAT_RATE",
  "TOTAL_AMOUNT",
  "CURRENCY",
  "CATEGORY",
];

export const FIELD_LABELS: Record<ExtractedFieldName, string> = {
  SUPPLIER_NAME: "Fornecedor",
  SUPPLIER_TAX_ID: "NIF do fornecedor",
  INVOICE_NUMBER: "Número da fatura",
  ISSUE_DATE: "Data de emissão",
  NET_AMOUNT: "Base tributável",
  VAT_AMOUNT: "IVA",
  VAT_RATE: "Taxa de IVA",
  TOTAL_AMOUNT: "Total",
  CURRENCY: "Moeda",
  CATEGORY: "Categoria",
};

/** Espelha `MinConfidenceRule.THRESHOLD` no backend (`com.docgrid.validation`). */
export const CONFIDENCE_THRESHOLD = 0.85;
