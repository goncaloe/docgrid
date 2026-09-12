import { Stack, Title } from "@mantine/core";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import type { DocumentListFilters } from "../../api/types";
import { DocumentFilters, EMPTY_DOCUMENT_FILTERS, type DocumentFiltersValue } from "./DocumentFilters";
import { DocumentsTable } from "./DocumentsTable";
import { useDocuments } from "./useDocuments";

function toApiFilters(filters: DocumentFiltersValue, page: number): DocumentListFilters {
  return {
    status: filters.status ?? undefined,
    supplierTaxId: filters.supplierTaxId !== "" ? filters.supplierTaxId : undefined,
    from: filters.from !== "" ? `${filters.from}T00:00:00Z` : undefined,
    to: filters.to !== "" ? `${filters.to}T23:59:59Z` : undefined,
    page,
    size: 20,
  };
}

export function DocumentsListPage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<DocumentFiltersValue>(EMPTY_DOCUMENT_FILTERS);
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useDocuments(toApiFilters(filters, page));

  function handleFiltersChange(next: DocumentFiltersValue) {
    setFilters(next);
    setPage(0);
  }

  const hasActiveFilters =
    filters.status !== null || filters.supplierTaxId !== "" || filters.from !== "" || filters.to !== "";

  return (
    <Stack gap="md">
      <Title order={2}>Documentos</Title>
      <DocumentFilters value={filters} onChange={handleFiltersChange} />
      <DocumentsTable
        page={data}
        isLoading={isLoading}
        isError={isError}
        onRetry={() => void refetch()}
        onPageChange={setPage}
        emptyMessage={
          hasActiveFilters
            ? "Sem resultados para este filtro."
            : "Ainda não submeteste nenhum documento. Vai a “Submeter” para o fazer."
        }
        onRowClick={(id) => navigate(`/review/${id}`)}
      />
    </Stack>
  );
}
