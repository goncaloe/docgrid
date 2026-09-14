import { Card, Progress, Table, Text, Title } from "@mantine/core";

import type { DashboardSupplierTotal } from "../../api/types";
import { formatEuro } from "../../format";
import { supplierRows } from "./dashboardChartData";

interface TopSuppliersProps {
  suppliers: DashboardSupplierTotal[];
}

export function TopSuppliers({ suppliers }: TopSuppliersProps) {
  const rows = supplierRows(suppliers);
  if (rows.length === 0) {
    return (
      <Card withBorder shadow="sm" padding="lg">
        <Title order={4}>Principais fornecedores</Title>
        <Text size="sm" c="dimmed">
          Nada que listar ainda.
        </Text>
      </Card>
    );
  }
  return (
    <Card withBorder shadow="sm" padding="lg">
      <Title order={4}>Principais fornecedores</Title>
      <Table striped verticalSpacing="sm">
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Fornecedor</Table.Th>
            <Table.Th ta="right">Documentos</Table.Th>
            <Table.Th ta="right">Total</Table.Th>
            <Table.Th>Quota</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((row) => (
            <Table.Tr key={row.taxId}>
              <Table.Td>{row.name}</Table.Td>
              <Table.Td ta="right">{String(row.documents)}</Table.Td>
              <Table.Td ta="right">{formatEuro(row.total)}</Table.Td>
              <Table.Td w={200}>
                <Progress.Root size="sm">
                  <Progress.Section value={row.percent} color="indigo.6">
                    <Progress.Label>{`${String(row.percent)} %`}</Progress.Label>
                  </Progress.Section>
                </Progress.Root>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Card>
  );
}