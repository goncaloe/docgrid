import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { queryWrapper } from "../../test/renderWithProviders";
import { server } from "../../test/setup";
import { useReviewQueueNavigation } from "./useReviewQueueNavigation";

function reviewQueuePage() {
  return {
    items: [
      { id: "doc-1", status: "NEEDS_REVIEW", supplierTaxId: null, invoiceNumber: null, issueDate: null, totalAmount: null, currency: null, createdAt: "2026-08-20T10:00:00Z" },
      { id: "doc-2", status: "NEEDS_REVIEW", supplierTaxId: null, invoiceNumber: null, issueDate: null, totalAmount: null, currency: null, createdAt: "2026-08-20T10:00:00Z" },
      { id: "doc-3", status: "NEEDS_REVIEW", supplierTaxId: null, invoiceNumber: null, issueDate: null, totalAmount: null, currency: null, createdAt: "2026-08-20T10:00:00Z" },
    ],
    page: 0,
    size: 50,
    totalElements: 3,
    totalPages: 1,
  };
}

describe("useReviewQueueNavigation", () => {
  it("devolve a posição (1-based) e o id do documento seguinte", async () => {
    server.use(http.get("/api/documents/review-queue", () => HttpResponse.json(reviewQueuePage())));

    const { result } = renderHook(() => useReviewQueueNavigation("doc-2"), { wrapper: queryWrapper() });

    await waitFor(() => {
      expect(result.current.position).toBe(2);
    });
    expect(result.current.total).toBe(3);
    expect(result.current.nextId).toBe("doc-3");
  });

  it("devolve tudo a null quando o documento não está na página carregada", async () => {
    server.use(http.get("/api/documents/review-queue", () => HttpResponse.json(reviewQueuePage())));

    const { result } = renderHook(() => useReviewQueueNavigation("doc-fora-da-pagina"), { wrapper: queryWrapper() });

    await waitFor(() => {
      expect(result.current.position).toBeNull();
    });
    expect(result.current.total).toBeNull();
    expect(result.current.nextId).toBeNull();
  });

  it("o último documento da página não tem seguinte", async () => {
    server.use(http.get("/api/documents/review-queue", () => HttpResponse.json(reviewQueuePage())));

    const { result } = renderHook(() => useReviewQueueNavigation("doc-3"), { wrapper: queryWrapper() });

    await waitFor(() => {
      expect(result.current.position).toBe(3);
    });
    expect(result.current.nextId).toBeNull();
  });
});
