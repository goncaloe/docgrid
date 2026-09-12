import { Alert, Anchor } from "@mantine/core";
import { Link } from "react-router-dom";

import type { ValidationResultResponse } from "../../api/types";

interface DuplicateCardProps {
  duplicateOfDocumentId: string;
  validationResults: ValidationResultResponse[];
}

export function DuplicateCard({ duplicateOfDocumentId, validationResults }: DuplicateCardProps) {
  const duplicateResult = validationResults.find((result) => result.ruleName === "DUPLICATE");

  return (
    <Alert color="orange" title="Possível duplicado">
      {duplicateResult?.message ?? "Este documento parece já ter sido submetido."}{" "}
      <Anchor component={Link} to={`/review/${duplicateOfDocumentId}`}>
        Ver o documento original
      </Anchor>
    </Alert>
  );
}
