import { useQuery } from "@tanstack/react-query";

import { listReviewQueue } from "../../api/documents";
import { REVIEW_QUEUE_POLL_INTERVAL_MS } from "./reviewQueuePolling";

const PAGE_SIZE = 50;

export interface ReviewQueueNavigation {
  /** Posição de 1 (não de 0) na fila, ou `null` se o documento não estiver na página carregada. */
  position: number | null;
  total: number | null;
  nextId: string | null;
}

/**
 * A posição de um documento na primeira página da fila, e o seguinte para onde avançar
 * depois de aprovar. Se o documento não estiver nessa página (fila maior que
 * `PAGE_SIZE`, ou já não está em revisão), devolve tudo a `null` — o contador simplesmente
 * não aparece, em vez de mostrar um número errado.
 */
export function useReviewQueueNavigation(documentId: string): ReviewQueueNavigation {
  const { data } = useQuery({
    queryKey: ["review-queue", 0],
    queryFn: () => listReviewQueue(0, PAGE_SIZE),
    refetchInterval: REVIEW_QUEUE_POLL_INTERVAL_MS,
  });

  if (data === undefined) {
    return { position: null, total: null, nextId: null };
  }

  const index = data.items.findIndex((item) => item.id === documentId);
  if (index === -1) {
    return { position: null, total: null, nextId: null };
  }

  return {
    position: index + 1,
    total: data.totalElements,
    nextId: data.items[index + 1]?.id ?? null,
  };
}
