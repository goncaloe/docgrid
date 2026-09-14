import { BarChart } from "@mantine/charts";
import { Card, Title } from "@mantine/core";

import type { DashboardMonthlyTotal } from "../../api/types";
import { formatEuro } from "../../format";
import { monthlyChartData } from "./dashboardChartData";

interface MonthlyChartProps {
  monthly: DashboardMonthlyTotal[];
}

export function MonthlyChart({ monthly }: MonthlyChartProps) {
  return (
    <Card withBorder shadow="sm" padding="lg">
      <Title order={4}>Evolução mensal</Title>
      {/* `h` fixo: o ResponsiveContainer do Recharts mede 0×0 em jsdom e desenha vazio. */}
      <BarChart
        h={260}
        data={monthlyChartData(monthly)}
        dataKey="month"
        series={[{ name: "total", color: "indigo.6" }]}
        withLegend={false}
        gridAxis="xy"
        valueFormatter={(value) => formatEuro(value)}
      />
    </Card>
  );
}