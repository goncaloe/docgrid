import { HttpResponse, http } from "msw";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { ExportResponse } from "../../api/types";
import { server } from "../../test/setup";
import { renderWithProviders } from "../../test/renderWithProviders";
import { ExportsPage } from "./ExportsPage";

const monthLabels = Array.from({ length: 12 }, (_, index) => ({
  month: index + 1,
  label: new Intl.DateTimeFormat("pt-PT", { month: "long" }).format(new Date(2026, index, 1)),
}));

function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function periodLabel(month: number): string {
  const name = monthLabels.find((entry) => entry.month === month)?.label ?? String(month);
  return `${capitalise(name)} 2026`;
}

function exportOf(month: number, id: string): ExportResponse {
  return {
    id,
    year: 2026,
    month,
    documentCount: 3,
    netTotal: 300,
    vatTotal: 69,
    total: 369,
    createdAt: "2026-08-01T10:00:00Z",
    documentsWithoutDate: 0,
  };
}

/** O mês por omissão é o corrente — o mesmo que o POST manda; não se mexe no Select. */
async function closeCurrentPeriod(user: UserEvent): Promise<void> {
  await user.click(screen.getByRole("button", { name: "Fechar período e exportar" }));
  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Fechar período e exportar" }));
}

describe("ExportsPage", () => {
  it("exporta chamando o POST e mostra a linha nova", async () => {
    const user = userEvent.setup();
    const monthNumber = new Date().getMonth() + 1;
    const created = exportOf(monthNumber, "11111111-1111-1111-1111-111111111111");
    let exports: ExportResponse[] = [];

    server.use(
      http.get("/api/exports", () => HttpResponse.json(exports)),
      http.post("/api/exports", async ({ request }) => {
        const body = (await request.json()) as { year: number; month: number };
        expect(body).toEqual({ year: new Date().getFullYear(), month: monthNumber });
        exports = [created];
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    renderWithProviders(<ExportsPage />, { withNotifications: true });

    expect(await screen.findByText("Nenhum período fechado neste ano.")).toBeInTheDocument();
    await closeCurrentPeriod(user);

    // A notificação confirma a exportação, e a lista invalida-se e mostra a linha nova.
    expect(await screen.findByText(`3 documentos incluídos em ${periodLabel(monthNumber)}.`)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(periodLabel(monthNumber))).toBeInTheDocument());
  });

  it("um mês já fechado avisa e não duplica a linha", async () => {
    const user = userEvent.setup();
    const monthNumber = new Date().getMonth() + 1;
    const existing = exportOf(monthNumber, "11111111-1111-1111-1111-111111111111");

    server.use(
      http.get("/api/exports", () => HttpResponse.json([existing])),
      http.post("/api/exports", () => HttpResponse.json(existing, { status: 200 })),
    );
    renderWithProviders(<ExportsPage />, { withNotifications: true });

    expect(await screen.findByText(periodLabel(monthNumber))).toBeInTheDocument();
    await closeCurrentPeriod(user);

    expect(await screen.findByText("Período já fechado")).toBeInTheDocument();
    expect(screen.getByText(/não se duplicou nada/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText(periodLabel(monthNumber))).toHaveLength(1));
  });

  it("o botão de descarga abre o URL que a API devolveu", async () => {
    const user = userEvent.setup();
    const existing = exportOf(7, "11111111-1111-1111-1111-111111111111");
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);

    server.use(
      http.get("/api/exports", () => HttpResponse.json([existing])),
      http.get("/api/exports/:id/file-url", () =>
        HttpResponse.json({
          url: "https://bucket.test/exports/2026-07.csv",
          expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
        }),
      ),
    );
    renderWithProviders(<ExportsPage />);

    await user.click(await screen.findByRole("button", { name: "CSV" }));
    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith("https://bucket.test/exports/2026-07.csv", "_blank", "noopener,noreferrer");
    });
  });
});