import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { setSession } from "../api/client";
import { EMPLOYEE_ACCESS_TOKEN, VALID_ACCESS_TOKEN, tokenResponse } from "../test/handlers";
import { renderWithProviders } from "../test/renderWithProviders";
import { RequireRole } from "./RequireRole";

describe("RequireRole", () => {
  it("bloqueia um papel fora da lista permitida", async () => {
    setSession(tokenResponse(EMPLOYEE_ACCESS_TOKEN), "colaborador@empresa.pt");

    renderWithProviders(
      <RequireRole allowed={["FINANCE", "MANAGER", "ADMIN"]}>
        <div>Fila de revisão</div>
      </RequireRole>,
    );

    await waitFor(() => expect(screen.getByText("Sem permissão")).toBeInTheDocument());
    expect(screen.queryByText("Fila de revisão")).not.toBeInTheDocument();
  });

  it("deixa passar um papel permitido", async () => {
    setSession(tokenResponse(VALID_ACCESS_TOKEN), "financeiro@empresa.pt");

    renderWithProviders(
      <RequireRole allowed={["FINANCE", "MANAGER", "ADMIN"]}>
        <div>Fila de revisão</div>
      </RequireRole>,
    );

    await waitFor(() => expect(screen.getByText("Fila de revisão")).toBeInTheDocument());
  });
});
