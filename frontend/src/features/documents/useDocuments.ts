import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { listDocuments } from "../../api/documents";
import { NON_TERMINAL_PROCESSING_STATUSES, type DocumentListFilters } from "../../api/types";

const POLL_INTERVAL_MS = 4_000;

export function useDocuments(filters: DocumentListFilters) {
  return useQuery({
    queryKey: ["documents", filters],
    queryFn: () => listDocuments(filters),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? [];
      const hasPendingDocument = items.some((doc) => NON_TERMINAL_PROCESSING_STATUSES.includes(doc.status));
      return hasPendingDocument ? POLL_INTERVAL_MS : false;
    },
  });
}
