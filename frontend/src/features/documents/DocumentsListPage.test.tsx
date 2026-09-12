import { HttpResponse, http } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { server } from "../../test/setup";
import { emptyPage } from "../../test/handlers";
import { renderWithProviders } from "../../test/renderWithProviders";
import { DocumentsListPage } from "./DocumentsListPage";

describe("DocumentsListPage", () => {
  it("mostra os documentos devolvidos pela API", async () => {
    renderWithProviders(<DocumentsListPage />);
    expect(await screen.findByText("FT 2026/1")).toBeInTheDocument();
  });

  it("mostra o estado vazio quando não há documentos nem filtros ativos", async () => {
    server.use(http.get("/api/documents", () => HttpResponse.json(emptyPage())));
    renderWithProviders(<DocumentsListPage />);
    expect(await screen.findByText(/Ainda não submeteste nenhum documento/)).toBeInTheDocument();
  });

  it("mostra o estado de erro com opção de tentar de novo", async () => {
    server.use(http.get("/api/documents", () => HttpResponse.json({ title: "Erro" }, { status: 500 })));
    renderWithProviders(<DocumentsListPage />);
    await waitFor(() => expect(screen.getByText("Não foi possível carregar os documentos")).toBeInTheDocument());
  });
});
