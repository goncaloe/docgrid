import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { getAccessToken } from "../../api/client";
import { renderWithProviders } from "../../test/renderWithProviders";
import { LoginPage } from "./LoginPage";

describe("LoginPage", () => {
  it("guarda a sessão e navega ao autenticar com sucesso", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />, { route: "/login" });

    await user.type(screen.getByLabelText("Email", { exact: false }), "ana@padaria.pt");
    await user.type(screen.getByLabelText("Palavra-passe", { exact: false }), "senha-correta");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    await waitFor(() => expect(getAccessToken()).not.toBeNull());
  });

  it("mostra uma mensagem de erro quando as credenciais são inválidas", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />, { route: "/login" });

    await user.type(screen.getByLabelText("Email", { exact: false }), "ana@padaria.pt");
    await user.type(screen.getByLabelText("Palavra-passe", { exact: false }), "senha-errada");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByText("Email ou palavra-passe incorretos.")).toBeInTheDocument();
  });
});
