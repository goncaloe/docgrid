import { http, HttpResponse } from "msw";

import type { DocumentSummaryResponse, PageResponse, TokenResponse } from "../api/types";

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
];

export { sampleDocument };
