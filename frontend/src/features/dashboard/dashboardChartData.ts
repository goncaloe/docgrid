import type { DashboardCategoryTotal, DashboardMonthlyTotal, DashboardSupplierTotal } from "../../api/types";

const CATEGORY_COLORS = ["indigo.6", "grape.6", "orange.6", "teal.6", "cyan.6", "red.6"];

function categoryColor(index: number): string {
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length] ?? "indigo.6";
}

const monthShortFormatter = new Intl.DateTimeFormat("pt-PT", { month: "short" });

/**
 * "2026-08" → "ago. 2026". O `Intl.DateTimeFormat` de pt-PT devolve "08/2026" quando
 * se pede mês e ano juntos — juntam-se à mão o mês curto e o ano.
 */
export function formatMonthLabel(month: string): string {
  const [year, monthNumber] = month.split("-").map(Number);
  if (year === undefined || monthNumber === undefined) {
    return month;
  }
  return `${monthShortFormatter.format(new Date(year, monthNumber - 1, 1))} ${String(year)}`;
}

/** Ponto do eixo x do gráfico mensal: rótulo do mês e total do mês. */
export interface MonthlyChartDatum {
  month: string;
  total: number;
}

export function monthlyChartData(monthly: DashboardMonthlyTotal[]): MonthlyChartDatum[] {
  return monthly.map((row) => ({ month: formatMonthLabel(row.month), total: row.total }));
}

export interface DonutSlice {
  name: string;
  value: number;
  color: string;
}

/** Muito mais que isto e o donut deixa de se ler; a cauda agrupa-se em "Outros". */
export const MAX_DONUT_SLICES = 6;

export function categoryChartData(categories: DashboardCategoryTotal[]): DonutSlice[] {
  const sorted = [...categories].sort((a, b) => b.total - a.total);
  if (sorted.length <= MAX_DONUT_SLICES) {
    return sorted.map((row, index) => ({
      name: row.category ?? "Sem categoria",
      value: row.total,
      color: categoryColor(index),
    }));
  }
  const head = sorted.slice(0, MAX_DONUT_SLICES - 1);
  const tail = sorted.slice(MAX_DONUT_SLICES - 1);
  return [
    ...head.map((row, index) => ({
      name: row.category ?? "Sem categoria",
      value: row.total,
      color: categoryColor(index),
    })),
    {
      name: "Outros",
      value: tail.reduce((sum, row) => sum + row.total, 0),
      color: categoryColor(MAX_DONUT_SLICES - 1),
    },
  ];
}

export interface SupplierChartRow {
  taxId: string;
  name: string;
  documents: number;
  total: number;
  /** 0–100, proporcional ao total mais alto — para a barra. */
  percent: number;
}

export function supplierRows(suppliers: DashboardSupplierTotal[]): SupplierChartRow[] {
  const maxTotal = suppliers.reduce((max, row) => (row.total > max ? row.total : max), 0);
  return suppliers.map((row) => ({
    taxId: row.taxId,
    name: row.name,
    documents: row.documents,
    total: row.total,
    percent: maxTotal === 0 ? 0 : Math.round((row.total / maxTotal) * 100),
  }));
}