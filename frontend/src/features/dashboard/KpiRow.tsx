import { Card, Grid, Text, Title } from "@mantine/core";

import type { DashboardOperations } from "../../api/types";
import { formatDuration, formatEuro, formatPercent } from "../../format";

interface KpiCardProps {
  label: string;
  value: string;
  /** Linha por baixo do valor; `null` para a omitir. */
  caption: string | null;
  /** Cartão da taxa de automatização: maior e em cor primária. */
  primary?: boolean;
}

function KpiCard({ label, value, caption, primary = false }: KpiCardProps) {
  return (
    <Card withBorder shadow="sm" padding="lg">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Title order={3} c={primary ? "indigo.6" : undefined}>
        {value}
      </Title>
      {caption !== null && (
        <Text size="sm" c="dimmed">
          {caption}
        </Text>
      )}
    </Card>
  );
}

interface KpiRowProps {
  operations: DashboardOperations;
  /** IVA do período — o dinheiro usa o `issue_date`, não o `created_at`. */
  vatTotal: number;
}

export function KpiRow({ operations, vatTotal }: KpiRowProps) {
  const hasAutomationRate = operations.automationRate !== null;
  const reviewSeconds = operations.averageReviewSeconds;

  return (
    <Grid>
      <Grid.Col span={{ base: 12, md: 6, lg: 6 }}>
        <KpiCard
          primary
          label="Taxa de automação"
          value={hasAutomationRate ? formatPercent(operations.automationRate) : "—"}
          caption={hasAutomationRate ? "passaram sem revisão humana" : "ainda sem documentos processados"}
        />
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 6, lg: 2 }}>
        <KpiCard label="Documentos processados" value={String(operations.processedDocuments)} caption={null} />
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 6, lg: 2 }}>
        <KpiCard
          label="Tempo médio de revisão"
          value={reviewSeconds !== null ? formatDuration(reviewSeconds) : "—"}
          caption={null}
        />
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 6, lg: 2 }}>
        <KpiCard label="IVA do período" value={formatEuro(vatTotal)} caption={null} />
      </Grid.Col>
    </Grid>
  );
}