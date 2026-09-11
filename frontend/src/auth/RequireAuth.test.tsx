import { screen, waitFor } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { setSession } from "../api/client";
import { VALID_ACCESS_TOKEN, tokenResponse } from "../test/handlers";
import { renderWithProviders } from "../test/renderWithProviders";
import { RequireAuth } from "./RequireAuth";

function ProtectedRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<div>Página de login</div>} />
      <Route
        path="/documents"
        element={
          <RequireAuth>
            <div>Documentos</div>
          </RequireAuth>
        }
      />
    </Routes>
  );
}

describe("RequireAuth", () => {
  it("redireciona para /login sem sessão", async () => {
    renderWithProviders(<ProtectedRoutes />, { route: "/documents" });
    await waitFor(() => expect(screen.getByText("Página de login")).toBeInTheDocument());
  });

  it("mostra o conteúdo protegido com sessão válida", async () => {
    setSession(tokenResponse(VALID_ACCESS_TOKEN), "financeiro@empresa.pt");
    renderWithProviders(<ProtectedRoutes />, { route: "/documents" });
    await waitFor(() => expect(screen.getByText("Documentos")).toBeInTheDocument());
  });
});
