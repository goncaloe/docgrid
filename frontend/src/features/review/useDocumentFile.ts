import { useQuery } from "@tanstack/react-query";

import { getFileUrl } from "../../api/documents";

/** Abaixo de `docgrid.upload.url-ttl` (5 minutos): o URL ainda é válido quando se usa a cópia em cache. */
const STALE_TIME_MS = 4 * 60 * 1000;

export function useDocumentFile(documentId: string) {
  return useQuery({
    queryKey: ["document-file-url", documentId],
    queryFn: () => getFileUrl(documentId),
    staleTime: STALE_TIME_MS,
    retry: 1,
  });
}
