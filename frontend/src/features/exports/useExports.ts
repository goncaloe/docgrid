import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { createExport, listExports, type CreateExportResult } from "../../api/exports";

export interface ClosePeriodInput {
  year: number;
  month: number;
}

export function useExports(year: number) {
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: ["exports", year],
    queryFn: () => listExports(year),
  });
  const create = useMutation({
    mutationFn: (input: ClosePeriodInput) => createExport(input.year, input.month),
  });

  /** Fecha o período e invalida a lista; devolve o status e a exportação para a notificação. */
  async function closePeriod(input: ClosePeriodInput): Promise<CreateExportResult> {
    const result = await create.mutateAsync(input);
    void queryClient.invalidateQueries({ queryKey: ["exports", year] });
    return result;
  }

  return { list, create, closePeriod };
}