import { Badge } from "@mantine/core";

import type { DocumentStatus } from "../../api/types";

const STATUS_LABELS: Record<DocumentStatus, string> = {
  UPLOADED: "Enviado",
  PROCESSING: "A processar",
  EXTRACTED: "Extraído",
  NEEDS_REVIEW: "Por rever",
  FAILED: "Falhou",
  APPROVED: "Aprovado",
  REJECTED: "Rejeitado",
  EXPORTED: "Exportado",
};

const STATUS_COLORS: Record<DocumentStatus, string> = {
  UPLOADED: "gray",
  PROCESSING: "blue",
  EXTRACTED: "cyan",
  NEEDS_REVIEW: "orange",
  FAILED: "red",
  APPROVED: "green",
  REJECTED: "red",
  EXPORTED: "grape",
};

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return <Badge color={STATUS_COLORS[status]}>{STATUS_LABELS[status]}</Badge>;
}
