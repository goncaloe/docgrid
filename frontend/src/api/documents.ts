import { apiFetch } from "./client";
import type {
  DocumentDetailResponse,
  DocumentListFilters,
  DocumentSummaryResponse,
  FileUrlResponse,
  PageResponse,
  UploadUrlRequest,
  UploadUrlResponse,
} from "./types";

function toQueryString(filters: DocumentListFilters): string {
  const params = new URLSearchParams();
  if (filters.status !== undefined) params.set("status", filters.status);
  if (filters.supplierTaxId !== undefined && filters.supplierTaxId !== "") {
    params.set("supplierTaxId", filters.supplierTaxId);
  }
  if (filters.from !== undefined) params.set("from", filters.from);
  if (filters.to !== undefined) params.set("to", filters.to);
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));
  return params.toString();
}

export function listDocuments(filters: DocumentListFilters): Promise<PageResponse<DocumentSummaryResponse>> {
  return apiFetch(`/api/documents?${toQueryString(filters)}`);
}

export function listReviewQueue(page: number, size = 20): Promise<PageResponse<DocumentSummaryResponse>> {
  return apiFetch(`/api/documents/review-queue?page=${String(page)}&size=${String(size)}`);
}

export function getDocument(id: string): Promise<DocumentDetailResponse> {
  return apiFetch(`/api/documents/${id}`);
}

export function requestUploadUrl(request: UploadUrlRequest): Promise<UploadUrlResponse> {
  return apiFetch("/api/documents/upload-url", { method: "POST", body: request });
}

export function getFileUrl(id: string): Promise<FileUrlResponse> {
  return apiFetch(`/api/documents/${id}/file-url`);
}
