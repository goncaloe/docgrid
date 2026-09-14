import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { getDashboard } from "../../api/dashboard";

/** `""` significa "sem filtro" — a API calcula os últimos 12 meses. */
export function useDashboard(from: string, to: string) {
  return useQuery({
    queryKey: ["dashboard", from, to],
    queryFn: () => getDashboard(from === "" ? undefined : from, to === "" ? undefined : to),
    placeholderData: keepPreviousData,
  });
}