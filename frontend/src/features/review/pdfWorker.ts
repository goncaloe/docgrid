import { pdfjs } from "react-pdf";

/**
 * O `pdfjs-dist` corre a análise do PDF num worker à parte, que não é carregado
 * automaticamente pelo `react-pdf`. Importado uma vez em `DocumentPreview.tsx`, antes de
 * qualquer `<Document>` ser desenhado.
 */
pdfjs.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();
