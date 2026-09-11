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

  function patchItem(id: string, patch: Partial<UploadItem>) {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  const uploadFiles = useCallback(
    (files: File[]) => {
      const newItems: UploadItem[] = files.map((file) => ({
        id: `upload-${String(nextId.current++)}`,
        file,
        status: "pending",
        progress: 0,
        error: null,
      }));
      setItems((current) => [...current, ...newItems]);

      for (const item of newItems) {
        void uploadOne(item);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  async function uploadOne(item: UploadItem) {
    const validationError = validateFile(item.file);
    if (validationError !== null) {
      patchItem(item.id, { status: "error", error: validationError });
      return;
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
    } catch (err) {
      patchItem(item.id, { status: "error", error: err instanceof Error ? err.message : "Envio falhou" });
    }
  }

  return { items, uploadFiles };
}
