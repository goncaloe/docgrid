import { Alert, Button, Group, Stack, Text, Title } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useState } from "react";

import { CategoryDonut } from "./CategoryDonut";
import { EMPTY_PERIOD_FILTER, PeriodFilter, type PeriodFilterValue } from "./PeriodFilter";
import { KpiRow } from "./KpiRow";
import { MonthlyChart } from "./MonthlyChart";
import { TopSuppliers } from "./TopSuppliers";
import { useDashboard } from "./useDashboard";

export function DashboardPage() {
  const [period, setPeriod] = useState<PeriodFilterValue>(EMPTY_PERIOD_FILTER);
  const { data, isLoading, isError, refetch } = useDashboard(period.from, period.to);

  if (isLoading && data === undefined) {
    return <Text c="dimmed">A carregar dashboard…</Text>;
  }

  if (isError || data === undefined) {
    return (
      <Alert color="red" icon={<IconAlertCircle size={16} />} title="Não foi possível carregar o dashboard">
        <Group>
          <Text size="sm">Tenta novamente dentro de momentos.</Text>
          <Button size="xs" variant="light" onClick={() => void refetch()}>
            Tentar de novo
          </Button>
        </Group>
      </Alert>
    );
  }

  const isEmpty = data.totals.documents === 0 && data.monthly.length === 0;

  return (
    <Stack gap="md">
      <Title order={2}>Dashboard</Title>
      <PeriodFilter value={period} onChange={setPeriod} />
      {isEmpty ? (
        <Text c="dimmed">Sem documentos no período escolhido.</Text>
      ) : (
        <>
          <KpiRow operations={data.operations} vatTotal={data.totals.vat} />
          <MonthlyChart monthly={data.monthly} />
          <CategoryDonut categories={data.categories} />
          <TopSuppliers suppliers={data.topSuppliers} />
        </>
      )}
    </Stack>
  );
}