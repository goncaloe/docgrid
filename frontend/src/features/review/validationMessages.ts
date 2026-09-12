import type { ExtractedFieldName, ValidationResultResponse } from "../../api/types";

/**
 * Que campos cada regra de `com.docgrid.validation` diz respeito, para mostrar a
 * mensagem junto ao campo em causa. `MIN_CONFIDENCE` e `DUPLICATE` ficam de fora: o
 * primeiro já se vê na percentagem de cada campo, o segundo tem cartão próprio
 * (`DuplicateCard`, que lê o mesmo resultado por outra via).
 */
const VALIDATION_RULE_FIELDS: Partial<Record<string, ExtractedFieldName[]>> = {
  ARITHMETIC: ["NET_AMOUNT", "VAT_AMOUNT", "TOTAL_AMOUNT"],
  VAT_RATE: ["VAT_RATE", "VAT_AMOUNT", "NET_AMOUNT"],
  TAX_ID: ["SUPPLIER_TAX_ID"],
  ISSUE_DATE: ["ISSUE_DATE"],
  APPROVAL_THRESHOLD: ["TOTAL_AMOUNT"],
  CATEGORY_SUGGESTION: ["CATEGORY"],
};

/**
 * As mensagens de um campo. Filtra por `message !== null` e não por `passed`: regras
 * informativas (`APPROVAL_THRESHOLD`, `CATEGORY_SUGGESTION`) trazem sempre `passed: true`
 * mas só têm mensagem quando têm algo a dizer.
 */
export function messagesForField(fieldName: ExtractedFieldName, results: ValidationResultResponse[]): string[] {
  return results.flatMap((result) => {
    if (result.message === null || VALIDATION_RULE_FIELDS[result.ruleName]?.includes(fieldName) !== true) {
      return [];
    }
    return [result.message];
  });
}
