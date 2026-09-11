/**
 * Tipos da API, copiados à mão a partir dos DTOs Java em
 * `backend/src/main/java/com/docgrid/{auth,document}/dto/`. Única fonte no frontend —
 * ninguém redeclara estas formas ad-hoc. Atualiza este ficheiro sempre que um DTO do
 * backend mudar.
 */

export type UserRole = "EMPLOYEE" | "FINANCE" | "MANAGER" | "ADMIN";

export type DocumentStatus =
  | "UPLOADED"
  | "PROCESSING"
  | "EXTRACTED"
  | "NEEDS_REVIEW"
  | "FAILED"
  | "APPROVED"
  | "REJECTED"
  | "EXPORTED";

export const NON_TERMINAL_PROCESSING_STATUSES: readonly DocumentStatus[] = ["UPLOADED", "PROCESSING"];

export type ExtractedFieldName =
  | "SUPPLIER_NAME"
  | "SUPPLIER_TAX_ID"
  | "INVOICE_NUMBER"
  | "ISSUE_DATE"
  | "NET_AMOUNT"
  | "VAT_AMOUNT"
  | "VAT_RATE"
  | "TOTAL_AMOUNT"
  | "CURRENCY"
  | "CATEGORY";

export type FieldSource = "AI" | "HUMAN";

// --- auth ---

export interface RegisterRequest {
  organizationName: string;
  organizationTaxId: string;
  adminEmail: string;
  adminPassword: string;
  adminFullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
}

// --- documents ---

export interface DocumentSummaryResponse {
  id: string;
  status: DocumentStatus;
  supplierTaxId: string | null;
  invoiceNumber: string | null;
  issueDate: string | null;
  totalAmount: string | null;
  currency: string | null;
  createdAt: string;
}

export interface ExtractedFieldResponse {
  fieldName: ExtractedFieldName;
  value: string;
  confidence: string | null;
  source: FieldSource;
}

export interface ValidationResultResponse {
  ruleName: string;
  severity: string;
  passed: boolean;
  message: string;
}

export interface DocumentDetailResponse {
  id: string;
  status: DocumentStatus;
  originalFilename: string;
  supplierTaxId: string | null;
  invoiceNumber: string | null;
  issueDate: string | null;
  netAmount: string | null;
  vatAmount: string | null;
  vatRate: string | null;
  totalAmount: string | null;
  currency: string | null;
  createdAt: string;
  updatedAt: string;
  fields: ExtractedFieldResponse[];
  validationResults: ValidationResultResponse[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UploadUrlRequest {
  filename: string;
  contentType: string;
  sizeBytes: number;
}

export interface UploadUrlResponse {
  documentId: string;
  storageKey: string;
  uploadUrl: string;
  httpMethod: string;
  requiredHeaders: Record<string, string>;
  expiresAt: string;
}

export interface FileUrlResponse {
  url: string;
  expiresAt: string;
}

export interface DocumentListFilters {
  status?: DocumentStatus;
  supplierTaxId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** Problema RFC 7807, devolvido por `GlobalExceptionHandler` para qualquer erro. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}
