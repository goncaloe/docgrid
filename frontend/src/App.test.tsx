import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { App } from "./App";
import { setSession } from "./api/client";
import { EMPLOYEE_ACCESS_TOKEN, tokenResponse } from "./test/handlers";
import { renderWithProviders } from "./test/renderWithProviders";

// `App` importa `ReviewPage` → `DocumentPreview` → `react-pdf` estaticamente; só o import
// já avalia o `pdfjs-dist`, que refere `DOMMatrix` (inexistente em jsdom). Não precisa de
// renderizar nada de verdade aqui, só de não rebentar ao carregar o módulo.
vi.mock("react-pdf", () => ({
  pdfjs: { GlobalWorkerOptions: {} },
  Document: () => null,
  Page: () => null,
}));

describe("App routing", () => {
  it("um EMPLOYEE não vê o conteúdo da fila de revisão ao navegar diretamente para o URL", async () => {
    setSession(tokenResponse(EMPLOYEE_ACCESS_TOKEN), "colaborador@empresa.pt");
    renderWithProviders(<App />, { route: "/review-queue" });

    await waitFor(() => expect(screen.getByText("Sem permissão")).toBeInTheDocument());
    expect(screen.queryByText("Fila de revisão")).not.toBeInTheDocument();
  });

  it("sem sessão, qualquer rota protegida redireciona para /login", async () => {
    renderWithProviders(<App />, { route: "/documents" });
    await waitFor(() => expect(screen.getByRole("heading", { name: "DocGrid" })).toBeInTheDocument());
  });
});
