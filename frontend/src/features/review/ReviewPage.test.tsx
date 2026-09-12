import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { AuthProvider } from "../../auth/AuthContext";
import { sampleDocumentDetail } from "../../test/handlers";
import { server } from "../../test/setup";
import { ReviewPage } from "./ReviewPage";

vi.mock("react-pdf", () => ({
  pdfjs: { GlobalWorkerOptions: {} },
  Document: () => null,
  Page: () => null,
}));

function LocationDisplay() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderReviewPage(documentId: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/review/${documentId}`]}>
          <AuthProvider>
            <LocationDisplay />
            <Routes>
              <Route path="/review/:id" element={<ReviewPage />} />
              <Route path="/review-queue" element={<div>Fila de revisão</div>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  );
}

function reviewQueuePageWithNext() {
  return {
    items: [
      { ...summaryFrom(sampleDocumentDetail), id: sampleDocumentDetail.id },
      { ...summaryFrom(sampleDocumentDetail), id: "next-document-id" },
    ],
    page: 0,
    size: 50,
    totalElements: 2,
    totalPages: 1,
  };
}

function summaryFrom(detail: typeof sampleDocumentDetail) {
  return {
    status: detail.status,
    supplierTaxId: detail.supplierTaxId,
    invoiceNumber: detail.invoiceNumber,
    issueDate: detail.issueDate,
    totalAmount: detail.totalAmount,
    currency: detail.currency,
    createdAt: detail.createdAt,
  };
}

describe("ReviewPage", () => {
  it("mostra a posição do documento na fila", async () => {
    server.use(http.get("/api/documents/review-queue", () => HttpResponse.json(reviewQueuePageWithNext())));

    renderReviewPage(sampleDocumentDetail.id);

    expect(await screen.findByText("1 de 2")).toBeInTheDocument();
  });

  it("Ctrl+Enter aprova e navega para o documento seguinte da fila", async () => {
    server.use(http.get("/api/documents/review-queue", () => HttpResponse.json(reviewQueuePageWithNext())));
    let approveCalled = false;
    server.use(
      http.post(`/api/documents/${sampleDocumentDetail.id}/approve`, () => {
        approveCalled = true;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    fireEvent.keyDown(document.body, { key: "Enter", ctrlKey: true });

    await waitFor(() => {
      expect(approveCalled).toBe(true);
    });
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent("/review/next-document-id");
    });
  });

  it("Esc abre a rejeição, e sem motivo não deixa confirmar", async () => {
    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    fireEvent.keyDown(document.body, { key: "Escape" });

    const rejectButton = await screen.findByRole("button", { name: "Rejeitar" });
    expect(rejectButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "Não é uma fatura" } });
    expect(rejectButton).toBeEnabled();
  });

  it("um duplicado exige confirmação explícita antes de aprovar", async () => {
    server.use(
      // Registado antes do genérico "/api/documents/:id" (que também bateria certo com
      // "/review-queue" como se fosse o id) para o MSW escolher o mais específico.
      http.get("/api/documents/review-queue", () => HttpResponse.json({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })),
      http.get("/api/documents/:id", () =>
        HttpResponse.json({ ...sampleDocumentDetail, duplicateOfDocumentId: "original-document-id" }),
      ),
    );
    let approveCalled = false;
    server.use(
      http.post(`/api/documents/${sampleDocumentDetail.id}/approve`, () => {
        approveCalled = true;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    fireEvent.click(screen.getByRole("button", { name: /^Aprovar/ }));

    expect(await screen.findByText("Aprovar um possível duplicado?")).toBeInTheDocument();
    expect(approveCalled).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Aprovar mesmo assim" }));

    await waitFor(() => {
      expect(approveCalled).toBe(true);
    });
  });

  it("insistir no atalho não aprova um duplicado por cima do aviso", async () => {
    server.use(
      http.get("/api/documents/review-queue", () =>
        HttpResponse.json({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }),
      ),
      http.get("/api/documents/:id", () =>
        HttpResponse.json({ ...sampleDocumentDetail, duplicateOfDocumentId: "original-document-id" }),
      ),
    );
    let approveCalled = false;
    server.use(
      http.post(`/api/documents/${sampleDocumentDetail.id}/approve`, () => {
        approveCalled = true;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    // Quem despacha uma fila carrega em Ctrl+Enter em cadência. O primeiro abre o aviso;
    // o segundo não pode valer por confirmação — senão o aviso nunca chega a ser lido.
    fireEvent.keyDown(document.body, { key: "Enter", ctrlKey: true });
    expect(await screen.findByText("Aprovar um possível duplicado?")).toBeInTheDocument();

    fireEvent.keyDown(document.body, { key: "Enter", ctrlKey: true });

    await waitFor(() => {
      expect(screen.getByText("Aprovar um possível duplicado?")).toBeInTheDocument();
    });
    expect(approveCalled).toBe(false);
  });

  it("Esc fecha a rejeição em vez de a reabrir", async () => {
    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    fireEvent.keyDown(document.body, { key: "Escape" });
    expect(await screen.findByLabelText(/Motivo/)).toBeInTheDocument();

    // O `Modal` do Mantine fecha-se sozinho no Escape. Se o atalho global continuar
    // armado, o mesmo evento volta a abri-lo e o modal fica preso — sem saída pelo teclado.
    fireEvent.keyDown(document.body, { key: "Escape" });

    await waitFor(() => {
      expect(screen.queryByLabelText(/Motivo/)).not.toBeInTheDocument();
    });
  });

  it("focar um campo marca o polígono correspondente como ativo na pré-visualização", async () => {
    const { container } = renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    const netAmountInput = await screen.findByLabelText(/Base tributável/);
    fireEvent.focus(netAmountInput);

    await waitFor(() => {
      const active = container.querySelector('polygon[data-field="NET_AMOUNT"]');
      const inactive = container.querySelector('polygon[data-field="SUPPLIER_NAME"]');
      expect(active?.getAttribute("class")).not.toBe(inactive?.getAttribute("class"));
    });
  });

  it("um documento aprovado não mostra os botões de aprovar ou rejeitar", async () => {
    server.use(
      http.get("/api/documents/review-queue", () => HttpResponse.json({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })),
      http.get("/api/documents/:id", () => HttpResponse.json({ ...sampleDocumentDetail, status: "APPROVED" })),
    );

    renderReviewPage(sampleDocumentDetail.id);
    await screen.findByText(sampleDocumentDetail.originalFilename);

    expect(screen.queryByRole("button", { name: /^Aprovar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Rejeitar" })).not.toBeInTheDocument();
  });
});
