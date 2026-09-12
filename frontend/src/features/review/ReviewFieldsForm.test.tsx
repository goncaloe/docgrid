import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import type { ExtractedFieldResponse, ValidationResultResponse } from "../../api/types";
import { server } from "../../test/setup";
import { ReviewFieldsForm } from "./ReviewFieldsForm";

const baseFields: ExtractedFieldResponse[] = [
  { fieldName: "SUPPLIER_NAME", value: "Cantina do Zé, Lda.", confidence: 0.97, source: "AI", page: 1, boundingBox: null },
  { fieldName: "SUPPLIER_TAX_ID", value: "505123452", confidence: 0.6, source: "AI", page: 1, boundingBox: null },
  { fieldName: "INVOICE_NUMBER", value: "FT 2026/1", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "ISSUE_DATE", value: "2026-08-20", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "NET_AMOUNT", value: "100.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "VAT_AMOUNT", value: "23.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "VAT_RATE", value: "23.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "TOTAL_AMOUNT", value: "123.00", confidence: 0.95, source: "AI", page: 1, boundingBox: null },
  { fieldName: "CURRENCY", value: "EUR", confidence: 0.99, source: "AI", page: 1, boundingBox: null },
];

function renderForm(fields: ExtractedFieldResponse[], validationResults: ValidationResultResponse[] = []) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <ReviewFieldsForm
          documentId="doc-1"
          fields={fields}
          validationResults={validationResults}
          editable
          onFocusField={vi.fn()}
          onBlurField={vi.fn()}
          categoryDraft=""
          onCategoryDraftChange={vi.fn()}
        />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

describe("ReviewFieldsForm", () => {
  it("destaca um campo abaixo do limiar de confiança com a percentagem visível", () => {
    renderForm(baseFields);
    expect(screen.getByText("60%")).toBeInTheDocument();
  });

  it("sair do campo sem alterar o valor não faz nenhum pedido", () => {
    let patchCalls = 0;
    server.use(
      http.patch("/api/documents/doc-1/fields/INVOICE_NUMBER", () => {
        patchCalls++;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderForm(baseFields);

    const input = screen.getByLabelText(/Número da fatura/);
    fireEvent.focus(input);
    fireEvent.blur(input);

    expect(patchCalls).toBe(0);
  });

  it("alterar um campo e sair grava um PATCH uma única vez", async () => {
    let patchCalls = 0;
    server.use(
      http.patch("/api/documents/doc-1/fields/INVOICE_NUMBER", () => {
        patchCalls++;
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderForm(baseFields);

    const input = screen.getByLabelText(/Número da fatura/);
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "FT 2026/99" } });
    fireEvent.blur(input);

    await waitFor(() => {
      expect(patchCalls).toBe(1);
    });
  });

  it("a mensagem do ARITHMETIC aparece junto ao total e não junto ao NIF", () => {
    const validationResults: ValidationResultResponse[] = [
      {
        ruleName: "ARITHMETIC",
        severity: "WARNING",
        passed: false,
        message: "A base mais o IVA não bate certo com o total.",
      },
    ];
    renderForm(baseFields, validationResults);

    expect(screen.getAllByText("A base mais o IVA não bate certo com o total.")).toHaveLength(3);
    const taxIdInput = screen.getByLabelText(/NIF do fornecedor/);
    expect(taxIdInput).not.toHaveAttribute("aria-invalid", "true");
  });
});
