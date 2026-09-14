/**
 * Tipos da API, copiados à mão a partir dos DTOs Java em
 * `backend/src/main/java/com/docgrid/{auth,document}/dto/`. Única fonte no frontend —
 * ninguém redeclara estas formas ad-hoc. Atualiza este ficheiro sempre que um DTO do
 * backend mudar.
 *
 * Correspondência de tipos Java → JSON: `BigDecimal` serializa como **número** (montantes,
 * taxas, grau de confiança), `Instant` e `LocalDate` como string ISO, `UUID` e `URI` como
 * string.
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
  totalAmount: number | null;
  currency: string | null;
  createdAt: string;
}

export interface PolygonPoint {
  x: number;
  y: number;
}

export interface ExtractedFieldResponse {
  fieldName: ExtractedFieldName;
  value: string;
  /** Entre 0 e 1. `null` num campo corrigido à mão — a origem `HUMAN` já diz que não há incerteza. */
  confidence: number | null;
  source: FieldSource;
  /** A página onde o campo foi lido. `null` num campo corrigido à mão. */
  page: number | null;
  /** O polígono que cerca o campo, normalizado 0–1. `null` num campo corrigido à mão. */
  boundingBox: PolygonPoint[] | null;
}

export interface ValidationResultResponse {
  ruleName: string;
  severity: string;
  passed: boolean;
  /** `null` quando a regra passa sem nada a assinalar. */
  message: string | null;
}

export interface DocumentDetailResponse {
  id: string;
  status: DocumentStatus;
  originalFilename: string;
  contentType: string;
  /** O documento aprovado com o mesmo NIF+número, ou o mesmo ficheiro; `null` sem duplicado. */
  duplicateOfDocumentId: string | null;
  supplierTaxId: string | null;
  invoiceNumber: string | null;
  issueDate: string | null;
  netAmount: number | null;
  vatAmount: number | null;
  vatRate: number | null;
  totalAmount: number | null;
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

export interface FieldCorrectionRequest {
  value: string;
}

/** @param category escolhida pelo revisor; `null` se não escolheu nenhuma */
export interface ApproveRequest {
  category: string | null;
}

export interface RejectRequest {
  reason: string;
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

// --- dashboard ---

export interface DashboardPeriodTotals {
  documents: number;
  net: number;
  vat: number;
  total: number;
}

export interface DashboardOperations {
  /** Documentos cujo estado já saiu de UPLOADED/PROCESSING/FAILED (por `created_at`). */
  processedDocuments: number;
  /** Fração 0–1 de documentos que nunca passaram por revisão humana; `null` sem documentos processados. */
  automationRate: number | null;
  /** Segundos entre a primeira passagem a NEEDS_REVIEW e a decisão; `null` sem ciclos concluidos. */
  averageReviewSeconds: number | null;
}

export interface DashboardMonthlyTotal {
  /** "yyyy-MM" */
  month: string;
  documents: number;
  net: number;
  vat: number;
  total: number;
}

export interface DashboardCategoryTotal {
  /** `null` = "sem categoria" — o rótulo é do frontend, nunca da API. */
  category: string | null;
  documents: number;
  total: number;
}

export interface DashboardSupplierTotal {
  taxId: string;
  name: string;
  documents: number;
  total: number;
}

export interface DashboardResponse {
  totals: DashboardPeriodTotals;
  operations: DashboardOperations;
  monthly: DashboardMonthlyTotal[];
  categories: DashboardCategoryTotal[];
  topSuppliers: DashboardSupplierTotal[];
}

// --- exports ---

export interface CreateExportRequest {
  /** Entre 2020 e o ano corrente (validação `@Min`/`@Max` do backend). */
  year: number;
  /** 1–12 */
  month: number;
}

export interface ExportResponse {
  id: string;
  year: number;
  month: number;
  documentCount: number;
  netTotal: number;
  vatTotal: number;
  total: number;
  createdAt: string;
  /** Aprovados sem `issue_date` que ficaram fora do ficheiro. */
  documentsWithoutDate: number;
}

export interface ExportFileUrlResponse {
  url: string;
  expiresAt: string;
}
