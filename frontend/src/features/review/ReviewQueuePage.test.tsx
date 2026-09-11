import { HttpResponse, http } from "msw";
import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { sampleDocument } from "../../test/handlers";
import { server } from "../../test/setup";
import { renderWithProviders } from "../../test/renderWithProviders";
import { ReviewQueuePage } from "./ReviewQueuePage";

describe("ReviewQueuePage", () => {
  it("usa o endpoint da fila de revisão e não mostra ações de aprovar/rejeitar", async () => {
    let requestedPath = "";
    server.use(
      http.get("/api/documents/review-queue", ({ request }) => {
        requestedPath = new URL(request.url).pathname;
        return HttpResponse.json({ items: [sampleDocument], page: 0, size: 20, totalElements: 1, totalPages: 1 });
      }),
    );

    renderWithProviders(<ReviewQueuePage />);

    expect(await screen.findByText("FT 2026/1")).toBeInTheDocument();
    expect(requestedPath).toBe("/api/documents/review-queue");
    expect(screen.queryByRole("button", { name: /aprovar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /rejeitar/i })).not.toBeInTheDocument();
  });
});
