import { DonutChart } from "@mantine/charts";
import { Card, Text, Title } from "@mantine/core";

import type { DashboardCategoryTotal } from "../../api/types";
import { formatEuro } from "../../format";
import { categoryChartData } from "./dashboardChartData";

interface CategoryDonutProps {
  categories: DashboardCategoryTotal[];
}

export function CategoryDonut({ categories }: CategoryDonutProps) {
  const data = categoryChartData(categories);
  if (data.length === 0) {
    return (
      <Card withBorder shadow="sm" padding="lg">
        <Title order={4}>Por categoria</Title>
        <Text size="sm" c="dimmed">
          Nada que repartir ainda.
        </Text>
      </Card>
    );
  }
  return (
    <Card withBorder shadow="sm" padding="lg">
      <Title order={4}>Por categoria</Title>
      <DonutChart
        data={data}
        withLabels
        labelsType="percent"
        size={200}
        valueFormatter={(value) => formatEuro(value)}
      />
    </Card>
  );
}