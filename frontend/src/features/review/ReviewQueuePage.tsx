import { Stack, Title } from "@mantine/core";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { listReviewQueue } from "../../api/documents";
import { DocumentsTable } from "../documents/DocumentsTable";

const POLL_INTERVAL_MS = 4_000;

export function ReviewQueuePage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["review-queue", page],
    queryFn: () => listReviewQueue(page),
    placeholderData: keepPreviousData,
    refetchInterval: POLL_INTERVAL_MS,
  });

  return (
    <Stack gap="md">
      <Title order={2}>Fila de revisão</Title>
      <DocumentsTable
        page={data}
        isLoading={isLoading}
        isError={isError}
        onRetry={() => void refetch()}
        onPageChange={setPage}
        emptyMessage="Não há documentos à espera de revisão."
      />
    </Stack>
  );
}
