import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { server } from "../../test/setup";
import { useDocumentUpload, type XhrFactory } from "./useDocumentUpload";

function fakeFile(name: string, type: string, sizeBytes: number): File {
  return new File([new Uint8Array(sizeBytes)], name, { type });
}

interface FakeXhr {
  open: () => void;
  setRequestHeader: () => void;
  upload: { onprogress: ((event: ProgressEvent) => void) | null };
  onload: (() => void) | null;
  onerror: (() => void) | null;
  status: number;
  send: (this: FakeXhr) => void;
}

/** XHR falso: simula um progresso e conclui com sucesso, sem tocar na rede. */
function successfulXhrFactory(): XhrFactory {
  return () => {
    const xhr: FakeXhr = {
      open: vi.fn(),
      setRequestHeader: vi.fn(),
      upload: { onprogress: null },
      onload: null,
      onerror: null,
      status: 200,
      send() {
        this.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 100 } as ProgressEvent);
        this.upload.onprogress?.({ lengthComputable: true, loaded: 100, total: 100 } as ProgressEvent);
        this.onload?.();
      },
    };
    return xhr as unknown as XMLHttpRequest;
  };
}

function failingXhrFactory(): XhrFactory {
  return () => {
    const xhr: FakeXhr = {
      open: vi.fn(),
      setRequestHeader: vi.fn(),
      upload: { onprogress: null },
      onload: null,
      onerror: null,
      status: 500,
      send() {
        this.onload?.();
      },
    };
    return xhr as unknown as XMLHttpRequest;
  };
}

describe("useDocumentUpload", () => {
  it("segue o progresso até 'done' num envio bem-sucedido", async () => {
    server.use(
      http.post("/api/documents/upload-url", () =>
        HttpResponse.json({
          documentId: "doc-1",
          storageKey: "org/1/2026/09/doc-1.pdf",
          uploadUrl: "http://localstack.test/bucket/doc-1.pdf",
          httpMethod: "PUT",
          requiredHeaders: { "content-type": "application/pdf" },
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
        }),
      ),
    );

    const { result } = renderHook(() => useDocumentUpload(successfulXhrFactory()));
    act(() => result.current.uploadFiles([fakeFile("fatura.pdf", "application/pdf", 1024)]));

    await waitFor(() => expect(result.current.items[0]?.status).toBe("done"));
    expect(result.current.items[0]?.progress).toBe(100);
  });

  it("um ficheiro inválido fica em erro sem pedir upload-url", async () => {
    const { result } = renderHook(() => useDocumentUpload(successfulXhrFactory()));
    act(() => result.current.uploadFiles([fakeFile("virus.exe", "application/x-msdownload", 1024)]));

    await waitFor(() => expect(result.current.items[0]?.status).toBe("error"));
    expect(result.current.items[0]?.error).toMatch(/não suportado/);
  });

  it("a falha de um ficheiro não impede os outros de terminar", async () => {
    server.use(
      http.post("/api/documents/upload-url", () =>
        HttpResponse.json({
          documentId: "doc-2",
          storageKey: "org/1/2026/09/doc-2.pdf",
          uploadUrl: "http://localstack.test/bucket/doc-2.pdf",
          httpMethod: "PUT",
          requiredHeaders: {},
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
        }),
      ),
    );

    let call = 0;
    const mixedXhrFactory: XhrFactory = () => {
      call += 1;
      return call === 1 ? failingXhrFactory()() : successfulXhrFactory()();
    };

    const { result } = renderHook(() => useDocumentUpload(mixedXhrFactory));
    act(() =>
      result.current.uploadFiles([
        fakeFile("a.pdf", "application/pdf", 1024),
        fakeFile("b.pdf", "application/pdf", 1024),
      ]),
    );

    await waitFor(() => {
      expect(result.current.items[0]?.status).toBe("error");
      expect(result.current.items[1]?.status).toBe("done");
    });
  });
});
