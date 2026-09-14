import { Button, Group, Select, Stack, Table, Text, Title } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useState } from "react";

import { getExportFileUrl } from "../../api/exports";
import type { ExportResponse } from "../../api/types";
import { formatDate, formatEuro } from "../../format";
import { ConfirmCloseModal } from "./ConfirmCloseModal";
import { useExports } from "./useExports";

const monthFormatter = new Intl.DateTimeFormat("pt-PT", { month: "long" });
const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => ({
  value: String(index + 1),
  label: monthFormatter.format(new Date(2000, index, 1)),
}));

function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function exportRowKey(exported: ExportResponse): string {
  return exported.id;
}

export function ExportsPage() {
  // Lista do ano corrente: o fecho parte sempre do mês atual. Mudar de ano não está
  // no âmbito desta etapa.
  const year = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const [month, setMonth] = useState(String(currentMonth));
  const [confirmOpened, setConfirmOpened] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const { list, create, closePeriod } = useExports(year);

  function monthLabel(monthNumber: number, yearNumber: number): string {
    return capitalise(`${monthFormatter.format(new Date(yearNumber, monthNumber - 1, 1))} ${String(yearNumber)}`);
  }

  async function handleConfirmClose() {
    setConfirmOpened(false);
    const selectedMonth = Number(month);
    try {
      const { status, export: exported } = await closePeriod({ year, month: selectedMonth });
      if (status === 200) {
        notifications.show({
          title: "Período já fechado",
          message: "Este mês já estava exportado — não se duplicou nada.",
        });
      } else {
        notifications.show({
          title: "Período exportado",
          message: `${String(exported.documentCount)} documentos incluídos em ${monthLabel(selectedMonth, year)}.`,
        });
      }
    } catch (error) {
      notifications.show({
        color: "red",
        title: "Não foi possível exportar",
        message: error instanceof Error ? error.message : "Tenta novamente.",
      });
    }
  }

  async function handleDownload(id: string) {
    setDownloadingId(id);
    try {
      const { url } = await getExportFileUrl(id);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (error) {
      notifications.show({
        color: "red",
        title: "Não foi possível descarregar",
        message: error instanceof Error ? error.message : "Tenta novamente.",
      });
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <Stack gap="md">
      <Title order={2}>Exportações</Title>
      <Group align="flex-end" wrap="wrap">
        <Select
          label="Mês"
          data={MONTH_OPTIONS}
          value={month}
          onChange={(value) => setMonth(value ?? "")}
          comboboxProps={{ keepMounted: false }}
          w={180}
        />
        <Button onClick={() => setConfirmOpened(true)} disabled={month === "" || create.isPending}>
          Fechar período e exportar
        </Button>
      </Group>

      {list.isLoading && list.data === undefined ? (
        <Text c="dimmed">A carregar exportações…</Text>
      ) : list.isError || list.data === undefined ? (
        <Text c="dimmed">Não foi possível carregar as exportações.</Text>
      ) : list.data.length === 0 ? (
        <Text c="dimmed">Nenhum período fechado neste ano.</Text>
      ) : (
        <Table striped verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Período</Table.Th>
              <Table.Th ta="right">Documentos</Table.Th>
              <Table.Th ta="right">Total</Table.Th>
              <Table.Th>Fechada</Table.Th>
              <Table.Th>Descarga</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {list.data.map((exported) => (
              <Table.Tr key={exportRowKey(exported)}>
                <Table.Td>{monthLabel(exported.month, exported.year)}</Table.Td>
                <Table.Td ta="right">{String(exported.documentCount)}</Table.Td>
                <Table.Td ta="right">{formatEuro(exported.total)}</Table.Td>
                <Table.Td>{formatDate(exported.createdAt.slice(0, 10))}</Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <Button size="xs" variant="light" onClick={() => void handleDownload(exported.id)} disabled={downloadingId !== null}>
                      CSV
                    </Button>
                    {exported.documentsWithoutDate > 0 && (
                      <Text size="xs" c="dimmed">
                        {`${String(exported.documentsWithoutDate)} aprovados sem data ficaram de fora`}
                      </Text>
                    )}
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <ConfirmCloseModal
        opened={confirmOpened}
        periodLabel={month === "" ? "" : monthLabel(Number(month), year)}
        onClose={() => setConfirmOpened(false)}
        onConfirm={() => void handleConfirmClose()}
      />
    </Stack>
  );
}