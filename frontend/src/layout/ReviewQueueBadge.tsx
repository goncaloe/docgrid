import { Badge } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";

import { listReviewQueue } from "../api/documents";

/** Contador da fila de revisão, visível só a quem já pode chegar a `/review-queue`. */
export function ReviewQueueBadge() {
  const { data } = useQuery({
    queryKey: ["review-queue-count"],
    queryFn: () => listReviewQueue(0, 1),
    refetchInterval: 15_000,
  });

  const count = data?.totalElements ?? 0;
  if (count === 0) {
    return null;
  }

  return (
    <Badge color="orange" variant="filled" aria-label={`${String(count)} documentos por rever`}>
      {count}
    </Badge>
  );
}
