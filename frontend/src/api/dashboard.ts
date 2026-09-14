import { apiFetch } from "./client";
import type { DashboardResponse } from "./types";

/**
 * Datas dos indicadores do dashboard. `from`/`to` são meses "yyyy-MM": o backend
 * interpreta-os como `YearMonth` e calcula o outro extremo quando falta.
 */
export function getDashboard(from?: string, to?: string): Promise<DashboardResponse> {
  const params = new URLSearchParams();
  if (from !== undefined) {
    params.set("from", from);
  }
  if (to !== undefined) {
    params.set("to", to);
  }
  const query = params.toString();
  return apiFetch(query === "" ? "/api/dashboard" : `/api/dashboard?${query}`);
}