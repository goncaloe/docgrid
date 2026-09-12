import type { ExtractedFieldName, ExtractedFieldResponse, UserRole, ValidationResultResponse } from "../../api/types";

const AMOUNT_FIELDS: ExtractedFieldName[] = ["NET_AMOUNT", "VAT_AMOUNT", "VAT_RATE", "TOTAL_AMOUNT"];

/**
 * Antecipa a mesma regra de `DocumentApprovalService.requireApprovalAuthority`: um total
 * acima do limite da organização, ou um montante corrigido à mão, só um `MANAGER`/`ADMIN`
 * aprova. O `GlobalExceptionHandler` devolve "Sem permissão para esta operação" sem
 * detalhe nesse caso — sinalizar aqui evita levar quem revê a um 403 sem explicação.
 */
export function needsManagerApproval(
  role: UserRole,
  fields: ExtractedFieldResponse[],
  validationResults: ValidationResultResponse[],
): boolean {
  if (role === "MANAGER" || role === "ADMIN") {
    return false;
  }
  const exceedsApprovalThreshold = validationResults.some((result) => result.ruleName === "APPROVAL_THRESHOLD");
  const hasHumanCorrectedAmount = fields.some(
    (field) => AMOUNT_FIELDS.includes(field.fieldName) && field.source === "HUMAN",
  );
  return exceedsApprovalThreshold || hasHumanCorrectedAmount;
}
