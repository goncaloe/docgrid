import { HttpResponse, http } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { DashboardResponse } from "../../api/types";
import { server } from "../../test/setup";
import { renderWithProviders } from "../../test/renderWithProviders";
import { DashboardPage } from "./DashboardPage";

const sampleDashboard: DashboardResponse = {
  totals: { documents: 10, net: 1000, vat: 230, total: 1230 },
  operations: { processedDocuments: 8, automationRate: 0.75, averageReviewSeconds: 90 },
  monthly: [
    { month: "2026-01", documents: 4, net: 400, vat: 92, total: 492 },
    { month: "2026-02", documents: 6, net: 600, vat: 138, total: 738 },
  ],
  categories: [
    { category: "Alimentação", documents: 6, total: 738 },
    { category: null, documents: 4, total: 492 },
  ],
  topSuppliers: [{ taxId: "509000000", name: "Cantina do Zé, Lda.", documents: 6, total: 738 }],
};

function useDashboard(response: DashboardResponse) {
  server.use(http.get("/api/dashboard", () => HttpResponse.json(response)));
}

describe("DashboardPage", () => {
  it("mostra a taxa de automatização calculada", async () => {
    useDashboard(sampleDashboard);
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText("75%")).toBeInTheDocument();
    expect(screen.getByText("passaram sem revisão humana")).toBeInTheDocument();
    // O rótulo «Sem categoria» vive dentro do SVG do donut → não renderiza em jsdom;
    // cobre-o o teste unitário de categoryChartData; aqui chega o título do cartão.
    expect(screen.getByText("Por categoria")).toBeInTheDocument();
  });

  it("sem documentos processados mostra «—» e nunca 0%", async () => {
    useDashboard({
      totals: { documents: 2, net: 200, vat: 46, total: 246 },
      operations: { processedDocuments: 0, automationRate: null, averageReviewSeconds: null },
      monthly: [{ month: "2026-02", documents: 2, net: 200, vat: 46, total: 246 }],
      categories: [],
      topSuppliers: [],
    });
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText("ainda sem documentos processados")).toBeInTheDocument();
    expect(screen.queryByText(/0\s*%/)).not.toBeInTheDocument();
  });

  it("mostra o estado vazio quando não existem documentos no período", async () => {
    useDashboard({
      totals: { documents: 0, net: 0, vat: 0, total: 0 },
      operations: { processedDocuments: 0, automationRate: null, averageReviewSeconds: null },
      monthly: [],
      categories: [],
      topSuppliers: [],
    });
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText("Sem documentos no período escolhido.")).toBeInTheDocument();
  });

  it("mostra o erro com opção de tentar de novo", async () => {
    server.use(http.get("/api/dashboard", () => HttpResponse.json({ title: "Erro" }, { status: 500 })));
    renderWithProviders(<DashboardPage />);

    await waitFor(() => expect(screen.getByText("Não foi possível carregar o dashboard")).toBeInTheDocument());
    expect(screen.getByText("Tentar de novo")).toBeInTheDocument();
  });
});