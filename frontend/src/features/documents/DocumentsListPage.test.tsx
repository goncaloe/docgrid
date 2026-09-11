import { HttpResponse, http } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { server } from "../../test/setup";
import { emptyPage } from "../../test/handlers";
import { renderWithProviders } from "../../test/renderWithProviders";
import { DocumentsListPage } from "./DocumentsListPage";

// Componentes do Mantine (Select/Combobox usados nos filtros) são lentos a montar em
// jsdom — vite.config.ts já alarga o testTimeout; alarga-se também as asserções
// assíncronas para não falharem por lentidão de ambiente.
const ASYNC_TIMEOUT = { timeout: 45_000 };

describe("DocumentsListPage", () => {
  it("mostra os documentos devolvidos pela API", async () => {
    renderWithProviders(<DocumentsListPage />);
    expect(await screen.findByText("FT 2026/1", {}, ASYNC_TIMEOUT)).toBeInTheDocument();
  });

  it("mostra o estado vazio quando não há documentos nem filtros ativos", async () => {
    server.use(http.get("/api/documents", () => HttpResponse.json(emptyPage())));
    renderWithProviders(<DocumentsListPage />);
    expect(await screen.findByText(/Ainda não submeteste nenhum documento/, {}, ASYNC_TIMEOUT)).toBeInTheDocument();
  });

  it("mostra o estado de erro com opção de tentar de novo", async () => {
    server.use(http.get("/api/documents", () => HttpResponse.json({ title: "Erro" }, { status: 500 })));
    renderWithProviders(<DocumentsListPage />);
    await waitFor(
      () => expect(screen.getByText("Não foi possível carregar os documentos")).toBeInTheDocument(),
      ASYNC_TIMEOUT,
    );
  });
});
