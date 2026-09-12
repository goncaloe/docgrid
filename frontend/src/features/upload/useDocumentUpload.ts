import { notifications } from "@mantine/notifications";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";

import { requestUploadUrl } from "../../api/documents";
import { validateFile } from "./uploadValidation";

export type UploadStatus = "pending" | "uploading" | "done" | "error";

export interface UploadItem {
  id: string;
  file: File;
  status: UploadStatus;
  progress: number;
  error: string | null;
}

/** O que o Dropzone devolve em `onReject`: o ficheiro e o porquê de não ter sido aceite. */
export interface RejectedFile {
  file: File;
  errors: readonly { readonly message: string }[];
}

/** Injetável nos testes, para não depender de um XHR real a falar com o S3. */
export type XhrFactory = () => XMLHttpRequest;

const defaultXhrFactory: XhrFactory = () => new XMLHttpRequest();

interface PutFileOptions {
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  file: File;
  xhrFactory: XhrFactory;
  onProgress: (percent: number) => void;
}

function putFile({ uploadUrl, method, headers, file, xhrFactory, onProgress }: PutFileOptions): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = xhrFactory();
    xhr.open(method, uploadUrl);
    for (const [name, value] of Object.entries(headers)) {
      xhr.setRequestHeader(name, value);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`Envio para o S3 falhou com o estado ${String(xhr.status)}`));
      }
    };
    xhr.onerror = () => reject(new Error("Envio para o S3 falhou"));
    xhr.send(file);
  });
}

export function useDocumentUpload(xhrFactory: XhrFactory = defaultXhrFactory) {
  const [items, setItems] = useState<UploadItem[]>([]);
  const nextId = useRef(0);
  const queryClient = useQueryClient();

  function patchItem(id: string, patch: Partial<UploadItem>) {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  function newItem(file: File, patch: Partial<UploadItem> = {}): UploadItem {
    return {
      id: `upload-${String(nextId.current++)}`,
      file,
      status: "pending",
      progress: 0,
      error: null,
      ...patch,
    };
  }

  const uploadFiles = useCallback(
    (files: File[]) => {
      const newItems = files.map((file) => newItem(file));
      setItems((current) => [...current, ...newItems]);

      void Promise.all(newItems.map(uploadOne)).then(announceBatch);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  /**
   * Ficheiros que o Dropzone recusou antes de chegarem aqui (tipo ou tamanho). Entram na
   * mesma lista, com a mesma mensagem que o resto da validação usa — um ficheiro largado
   * que desaparece sem explicação é o pior resultado possível.
   */
  const rejectFiles = useCallback(
    (rejections: RejectedFile[]) => {
      const rejectedItems = rejections.map((rejection) =>
        newItem(rejection.file, {
          status: "error",
          error: validateFile(rejection.file) ?? rejection.errors[0]?.message ?? "Ficheiro não aceite.",
        }),
      );
      setItems((current) => [...current, ...rejectedItems]);
    },
    [],
  );

  /** Um aviso por lote, não um por ficheiro: cinco toasts de uma vez não é informação. */
  function announceBatch(results: boolean[]) {
    const uploaded = results.filter(Boolean).length;
    const failed = results.length - uploaded;
    if (uploaded > 0) {
      void queryClient.invalidateQueries({ queryKey: ["documents"] });
      notifications.show({
        color: "green",
        title: uploaded === 1 ? "Documento submetido" : `${String(uploaded)} documentos submetidos`,
        message: "A extração começa dentro de momentos — segue o estado em “Documentos”.",
      });
    }
    if (failed > 0) {
      notifications.show({
        color: "red",
        title: failed === 1 ? "Um ficheiro não foi enviado" : `${String(failed)} ficheiros não foram enviados`,
        message: "O motivo está ao lado de cada ficheiro na lista.",
      });
    }
  }

  /** Resolve a `true` se o ficheiro chegou ao S3, a `false` se ficou pelo caminho. */
  async function uploadOne(item: UploadItem): Promise<boolean> {
    const validationError = validateFile(item.file);
    if (validationError !== null) {
      patchItem(item.id, { status: "error", error: validationError });
      return false;
    }
    patchItem(item.id, { status: "uploading" });
    try {
      const authorization = await requestUploadUrl({
        filename: item.file.name,
        contentType: item.file.type,
        sizeBytes: item.file.size,
      });
      await putFile({
        uploadUrl: authorization.uploadUrl,
        method: authorization.httpMethod,
        headers: authorization.requiredHeaders,
        file: item.file,
        xhrFactory,
        onProgress: (progress) => patchItem(item.id, { progress }),
      });
      patchItem(item.id, { status: "done", progress: 100 });
      return true;
    } catch (err) {
      patchItem(item.id, { status: "error", error: err instanceof Error ? err.message : "Envio falhou" });
      return false;
    }
  }

  return { items, uploadFiles, rejectFiles };
}
