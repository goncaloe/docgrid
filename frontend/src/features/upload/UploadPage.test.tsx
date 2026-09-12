import { fireEvent, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "../../test/setup";
import { renderWithProviders } from "../../test/renderWithProviders";
import { UploadPage } from "./UploadPage";

function pdf(name: string, sizeBytes = 1024): File {
  return new File([new Uint8Array(sizeBytes)], name, { type: "application/pdf" });
}

/**
 * Entrega ficheiros ao Dropzone pelo seu input escondido. Não se usa `userEvent.upload`:
 * esse filtra pelo atributo `accept` do input e recusa elementos escondidos, o que
 * eliminaria em silêncio justamente o caso que estes testes querem cobrir.
 */
function dropFiles(files: File[]): void {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (input === null) {
    throw new Error("O Dropzone não tem input de ficheiro");
  }
  fireEvent.change(input, { target: { files } });
}

describe("UploadPage", () => {
  it("mostra uma barra de progresso independente por ficheiro largado", async () => {
    let issued = 0;
    server.use(
      http.post("/api/documents/upload-url", () => {
        issued += 1;
        return HttpResponse.json({
          documentId: `doc-${String(issued)}`,
          storageKey: `org/1/2026/09/doc-${String(issued)}.pdf`,
          uploadUrl: "http://localstack.test/bucket/doc.pdf",
          httpMethod: "PUT",
          requiredHeaders: { "content-type": "application/pdf" },
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
        });
      }),
    );

    renderWithProviders(<UploadPage />);
    dropFiles(["a.pdf", "b.pdf", "c.pdf", "d.pdf", "e.pdf"].map((name) => pdf(name)));

    await waitFor(() => {
      expect(screen.getAllByRole("progressbar")).toHaveLength(5);
    });
    for (const name of ["a.pdf", "b.pdf", "c.pdf", "d.pdf", "e.pdf"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
      expect(screen.getByLabelText(`Progresso de ${name}`)).toBeInTheDocument();
    }
    expect(issued).toBe(5);
  });

  it("um ficheiro recusado pelo Dropzone aparece na lista com o motivo, não desaparece", async () => {
    renderWithProviders(<UploadPage />);
    dropFiles([
      new File([new Uint8Array(16)], "contrato.docx", {
        type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      }),
    ]);

    expect(await screen.findByText("contrato.docx")).toBeInTheDocument();
    expect(screen.getByText(/Tipo de ficheiro não suportado/)).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });
});
