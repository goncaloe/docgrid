import type { RefObject } from "react";
import { useState } from "react";
import { Alert, Button, Center, Loader, Stack, Text } from "@mantine/core";
import { useElementSize } from "@mantine/hooks";
import { Document, Page } from "react-pdf";

import type { ExtractedFieldName, ExtractedFieldResponse } from "../../api/types";
import { BoundingBoxOverlay } from "./BoundingBoxOverlay";
import styles from "./DocumentPreview.module.css";
import "./pdfWorker";
import { useDocumentFile } from "./useDocumentFile";

interface DocumentPreviewProps {
  documentId: string;
  contentType: string;
  fields: ExtractedFieldResponse[];
  activeField: ExtractedFieldName | null;
}

export function DocumentPreview({ documentId, contentType, fields, activeField }: DocumentPreviewProps) {
  const { data, isLoading, isError, refetch } = useDocumentFile(documentId);
  const { ref, width } = useElementSize<HTMLDivElement>();
  const [pdfError, setPdfError] = useState<string | null>(null);

  const activeFieldData = fields.find((field) => field.fieldName === activeField) ?? null;
  const page = activeFieldData?.page ?? 1;

  if (isLoading) {
    return (
      <Center h={300}>
        <Loader />
      </Center>
    );
  }

  if (isError || data === undefined) {
    return (
      <Alert color="red" title="Não foi possível carregar o documento">
        <Stack gap="xs">
          <Text size="sm">O URL de acesso pode ter expirado.</Text>
          <Button
            size="xs"
            onClick={() => {
              void refetch();
            }}
          >
            Tentar de novo
          </Button>
        </Stack>
      </Alert>
    );
  }

  const isPdf = contentType === "application/pdf";

  return (
    // `useElementSize` devolve um `RefObject<T | null>`, incompatível com o tipo de `ref`
    // de um elemento nativo nesta versão de `@types/react`; a forma é idêntica em runtime.
    <div ref={ref as RefObject<HTMLDivElement>} className={styles.wrapper}>
      {isPdf ? (
        pdfError !== null ? (
          <Alert color="red" title="Não foi possível abrir o PDF">
            {pdfError}
          </Alert>
        ) : (
          <Document
            file={data.url}
            loading={<Loader />}
            onLoadError={(error) => {
              setPdfError(error.message);
            }}
          >
            <Page
              pageNumber={page}
              width={width || undefined}
              renderTextLayer={false}
              renderAnnotationLayer={false}
            />
          </Document>
        )
      ) : (
        <img src={data.url} alt="Documento original" className={styles.image} />
      )}
      <BoundingBoxOverlay fields={fields} page={page} activeField={activeField} />
    </div>
  );
}
