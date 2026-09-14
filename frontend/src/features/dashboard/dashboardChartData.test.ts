import { describe, expect, it } from "vitest";

import type { DashboardCategoryTotal, DashboardMonthlyTotal, DashboardSupplierTotal } from "../../api/types";
import { MAX_DONUT_SLICES, categoryChartData, formatMonthLabel, monthlyChartData, supplierRows } from "./dashboardChartData";

describe("dashboardChartData", () => {
  it("etiqueta os meses do eixo em pt-PT", () => {
    expect(formatMonthLabel("2026-08")).toBe("ago. 2026");
  });

  it("passa a série mensal ao formato do gráfico", () => {
    const monthly: DashboardMonthlyTotal[] = [{ month: "2026-01", documents: 4, net: 400, vat: 92, total: 492 }];
    expect(monthlyChartData(monthly)).toEqual([{ month: "jan. 2026", total: 492 }]);
  });

  it("rótula «Sem categoria» a fatia sem categoria", () => {
    const categories: DashboardCategoryTotal[] = [
      { category: null, documents: 4, total: 492 },
      { category: "Alimentação", documents: 6, total: 738 },
    ];
    const slices = categoryChartData(categories);
    expect(slices).toEqual([
      { name: "Alimentação", value: 738, color: "indigo.6" },
      { name: "Sem categoria", value: 492, color: "grape.6" },
    ]);
  });

  it("agrupa a cauda em «Outros» quando há mais de MAX_DONUT_SLICES categorias", () => {
    const categories: DashboardCategoryTotal[] = Array.from({ length: 8 }, (_, index) => ({
      category: `Categoria ${String(index)}`,
      documents: 1,
      total: index,
    }));
    const slices = categoryChartData(categories);
    expect(slices).toHaveLength(MAX_DONUT_SLICES);
    expect(slices[MAX_DONUT_SLICES - 1]?.name).toBe("Outros");
    // Total preservado: a soma das fatias é a soma de todas as categorias.
    const total = slices.reduce((sum, slice) => sum + slice.value, 0);
    expect(total).toBe(categories.reduce((sum, row) => sum + row.total, 0));
  });

  it("calcula a barra proporcional ao total mais alto", () => {
    const suppliers: DashboardSupplierTotal[] = [
      { taxId: "A", name: "Fornecedor A", documents: 2, total: 100 },
      { taxId: "B", name: "Fornecedor B", documents: 6, total: 400 },
    ];
    const rows = supplierRows(suppliers);
    expect(rows.map((row) => row.percent)).toEqual([25, 100]);
  });

  it("não tem fornecedores: lista vazia", () => {
    expect(supplierRows([])).toEqual([]);
  });
});