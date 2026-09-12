import { apiFetch } from "./client";
import type {
  ApproveRequest,
  DocumentDetailResponse,
  DocumentListFilters,
  DocumentSummaryResponse,
  ExtractedFieldName,
  FieldCorrectionRequest,
  FileUrlResponse,
  PageResponse,
  RejectRequest,
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

export function correctField(id: string, fieldName: ExtractedFieldName, value: string): Promise<void> {
  const request: FieldCorrectionRequest = { value };
  return apiFetch(`/api/documents/${id}/fields/${fieldName}`, { method: "PATCH", body: request });
}

export function approveDocument(id: string, category: string | null = null): Promise<void> {
  const request: ApproveRequest = { category };
  return apiFetch(`/api/documents/${id}/approve`, { method: "POST", body: request });
}

export function rejectDocument(id: string, reason: string): Promise<void> {
  const request: RejectRequest = { reason };
  return apiFetch(`/api/documents/${id}/reject`, { method: "POST", body: request });
}
