import { Alert, Button, Group, Pagination, Table, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";

import type { DocumentSummaryResponse, PageResponse } from "../../api/types";
import { formatAmount, formatDate } from "../../format";
import { DocumentStatusBadge } from "./DocumentStatusBadge";

/** `createdAt` é uma `Instant` — ISO com fuso; `new Date()` parseia correctamente, o que `formatDate` não faz. */
const instantFormatter = new Intl.DateTimeFormat("pt-PT");

const columns: ColumnDef<DocumentSummaryResponse>[] = [
  { header: "Fornecedor (NIF)", accessorKey: "supplierTaxId", cell: (info) => info.getValue<string | null>() ?? "—" },
  { header: "Nº fatura", accessorKey: "invoiceNumber", cell: (info) => info.getValue<string | null>() ?? "—" },
  {
    header: "Data",
    accessorKey: "issueDate",
    cell: (info) => {
      const value = info.getValue<string | null>();
      return value === null ? "—" : formatDate(value);
    },
  },
  {
    header: "Total",
    id: "totalAmount",
    cell: (info) => formatAmount(info.row.original.totalAmount, info.row.original.currency),
  },
  {
    header: "Estado",
    accessorKey: "status",
    cell: (info) => <DocumentStatusBadge status={info.getValue<DocumentSummaryResponse["status"]>()} />,
  },
  {
    header: "Submetido em",
    accessorKey: "createdAt",
    cell: (info) => instantFormatter.format(new Date(info.getValue<string>())),
  },
];

interface DocumentsTableProps {
  page?: PageResponse<DocumentSummaryResponse>;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onPageChange: (page: number) => void;
  emptyMessage: string;
  onRowClick?: (id: string) => void;
}

export function DocumentsTable({
  page,
  isLoading,
  isError,
  onRetry,
  onPageChange,
  emptyMessage,
  onRowClick,
}: DocumentsTableProps) {
  const table = useReactTable({
    data: page?.items ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  if (isError) {
    return (
      <Alert color="red" icon={<IconAlertCircle size={16} />} title="Não foi possível carregar os documentos">
        <Group>
          <Text size="sm">Tenta novamente dentro de momentos.</Text>
          <Button size="xs" variant="light" onClick={onRetry}>
            Tentar de novo
          </Button>
        </Group>
      </Alert>
    );
  }

  if (isLoading && page === undefined) {
    return <Text c="dimmed">A carregar documentos…</Text>;
  }

  if (page !== undefined && page.items.length === 0) {
    return <Text c="dimmed">{emptyMessage}</Text>;
  }

  return (
    <>
      <Table.ScrollContainer minWidth={640}>
        <Table striped highlightOnHover verticalSpacing="sm">
          <Table.Thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <Table.Tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <Table.Th key={header.id}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </Table.Th>
                ))}
              </Table.Tr>
            ))}
          </Table.Thead>
          <Table.Tbody>
            {table.getRowModel().rows.map((row) => (
              <Table.Tr
                key={row.id}
                onClick={onRowClick === undefined ? undefined : () => onRowClick(row.original.id)}
                onKeyDown={
                  onRowClick === undefined
                    ? undefined
                    : (event) => {
                        if (event.key === "Enter") {
                          onRowClick(row.original.id);
                        }
                      }
                }
                tabIndex={onRowClick === undefined ? undefined : 0}
                style={onRowClick === undefined ? undefined : { cursor: "pointer" }}
              >
                {row.getVisibleCells().map((cell) => (
                  <Table.Td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {page !== undefined && page.totalPages > 1 && (
        <Group justify="center" mt="md">
          <Pagination total={page.totalPages} value={page.page + 1} onChange={(value) => onPageChange(value - 1)} />
        </Group>
      )}
    </>
  );
}
