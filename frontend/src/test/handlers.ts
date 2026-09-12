import { http, HttpResponse } from "msw";

import type { DocumentDetailResponse, DocumentSummaryResponse, PageResponse, TokenResponse } from "../api/types";

export const VALID_ACCESS_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  btoa(JSON.stringify({ sub: "11111111-1111-1111-1111-111111111111", org: "org-1", role: "FINANCE" })) +
  ".signature";

export const EMPLOYEE_ACCESS_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  btoa(JSON.stringify({ sub: "22222222-2222-2222-2222-222222222222", org: "org-1", role: "EMPLOYEE" })) +
  ".signature";

export function emptyPage<T>(): PageResponse<T> {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

export function tokenResponse(accessToken: string): TokenResponse {
  return {
    accessToken,
    accessTokenExpiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    refreshToken: "refresh-token-abc",
  };
}

const sampleDocument: DocumentSummaryResponse = {
  id: "33333333-3333-3333-3333-333333333333",
  status: "NEEDS_REVIEW",
  supplierTaxId: "509000000",
  invoiceNumber: "FT 2026/1",
  issueDate: "2026-08-20",
  totalAmount: 123.45,
  currency: "EUR",
  createdAt: "2026-08-20T10:00:00Z",
};

/** Imagem (não PDF): o caminho que os testes conseguem mesmo verificar em jsdom. */
const sampleDocumentDetail: DocumentDetailResponse = {
  id: sampleDocument.id,
  status: "NEEDS_REVIEW",
  originalFilename: "fatura.jpg",
  contentType: "image/jpeg",
  duplicateOfDocumentId: null,
  supplierTaxId: "509000000",
  invoiceNumber: "FT 2026/1",
  issueDate: "2026-08-20",
  netAmount: 100,
  vatAmount: 23,
  vatRate: 23,
  totalAmount: 123,
  currency: "EUR",
  createdAt: "2026-08-20T10:00:00Z",
  updatedAt: "2026-08-20T10:00:00Z",
  fields: [
    {
      fieldName: "SUPPLIER_NAME",
      value: "Cantina do Zé, Lda.",
      confidence: 0.97,
      source: "AI",
      page: 1,
      boundingBox: [
        { x: 0.1, y: 0.1 },
        { x: 0.4, y: 0.1 },
        { x: 0.4, y: 0.2 },
        { x: 0.1, y: 0.2 },
      ],
    },
    {
      fieldName: "SUPPLIER_TAX_ID",
      value: "509000000",
      confidence: 0.97,
      source: "AI",
      page: 1,
      boundingBox: [
        { x: 0.1, y: 0.25 },
        { x: 0.4, y: 0.25 },
        { x: 0.4, y: 0.3 },
        { x: 0.1, y: 0.3 },
      ],
    },
    {
      fieldName: "INVOICE_NUMBER",
      value: "FT 2026/1",
      confidence: 0.95,
      source: "AI",
      page: 1,
      boundingBox: [
        { x: 0.5, y: 0.1 },
        { x: 0.8, y: 0.1 },
        { x: 0.8, y: 0.2 },
        { x: 0.5, y: 0.2 },
      ],
    },
    { fieldName: "ISSUE_DATE", value: "2026-08-20", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
    {
      fieldName: "NET_AMOUNT",
      value: "100.00",
      confidence: 0.95,
      source: "AI",
      page: 1,
      boundingBox: [
        { x: 0.5, y: 0.5 },
        { x: 0.7, y: 0.5 },
        { x: 0.7, y: 0.6 },
        { x: 0.5, y: 0.6 },
      ],
    },
    { fieldName: "VAT_AMOUNT", value: "23.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
    { fieldName: "VAT_RATE", value: "23.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
    { fieldName: "TOTAL_AMOUNT", value: "123.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
    { fieldName: "CURRENCY", value: "EUR", confidence: 0.99, source: "AI", page: 1, boundingBox: null },
  ],
  validationResults: [],
};

export const handlers = [
  http.post("/api/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password !== "senha-correta") {
      return HttpResponse.json({ title: "Não autorizado", status: 401 }, { status: 401 });
    }
    return HttpResponse.json(tokenResponse(VALID_ACCESS_TOKEN), { status: 200 });
  }),
  http.post("/api/auth/refresh", () => HttpResponse.json(tokenResponse(VALID_ACCESS_TOKEN))),
  http.post("/api/auth/logout", () => new HttpResponse(null, { status: 204 })),
  http.get("/api/documents", () => HttpResponse.json({ ...emptyPage(), items: [sampleDocument], totalElements: 1 })),
  http.get("/api/documents/review-queue", () => HttpResponse.json(emptyPage())),
  http.get("/api/documents/:id", () => HttpResponse.json(sampleDocumentDetail)),
  http.patch("/api/documents/:id/fields/:fieldName", () => new HttpResponse(null, { status: 200 })),
  http.post("/api/documents/:id/approve", () => new HttpResponse(null, { status: 200 })),
  http.post("/api/documents/:id/reject", () => new HttpResponse(null, { status: 204 })),
  http.get("/api/documents/:id/file-url", () =>
    HttpResponse.json({ url: "https://bucket.test/fatura.jpg", expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString() }),
  ),
];

export { sampleDocument, sampleDocumentDetail };
