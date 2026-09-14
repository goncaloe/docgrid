import { apiFetch } from "./client";
import type { CreateExportRequest, ExportFileUrlResponse, ExportResponse } from "./types";

export interface CreateExportResult {
  /** HTTP 201 = criado agora; 200 = o período já estava fechado. */
  status: number;
  export: ExportResponse;
}

export function listExports(year: number): Promise<ExportResponse[]> {
  return apiFetch(`/api/exports?year=${String(year)}`);
}

export function createExport(year: number, month: number): Promise<CreateExportResult> {
  const request: CreateExportRequest = { year, month };
  let status = 0;
  // `apiFetch` só devolve o corpo; o status chega pela callback, que corre antes de a
  // promessa do corpo se resolver (mesma invocação assíncrona).
  const body = apiFetch<ExportResponse>("/api/exports", {
    method: "POST",
    body: request,
    onStatus: (value) => {
      status = value;
    },
  });
  return body.then((exported) => ({ status, export: exported }));
}

export function getExportFileUrl(id: string): Promise<ExportFileUrlResponse> {
  return apiFetch(`/api/exports/${id}/file-url`);
}