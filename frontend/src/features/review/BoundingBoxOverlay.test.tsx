import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { ExtractedFieldResponse } from "../../api/types";
import { BoundingBoxOverlay } from "./BoundingBoxOverlay";

const fields: ExtractedFieldResponse[] = [
  {
    fieldName: "SUPPLIER_NAME",
    value: "Cantina do Zé, Lda.",
    confidence: 0.98,
    source: "AI",
    page: 1,
    boundingBox: [
      { x: 0.1, y: 0.2 },
      { x: 0.4, y: 0.2 },
      { x: 0.4, y: 0.3 },
      { x: 0.1, y: 0.3 },
    ],
  },
  {
    fieldName: "TOTAL_AMOUNT",
    value: "123.00",
    confidence: 0.91,
    source: "AI",
    page: 1,
    boundingBox: [
      { x: 0.5, y: 0.6 },
      { x: 0.7, y: 0.6 },
      { x: 0.7, y: 0.7 },
      { x: 0.5, y: 0.7 },
    ],
  },
  {
    fieldName: "INVOICE_NUMBER",
    value: "FT 2026/1",
    confidence: null,
    source: "HUMAN",
    page: null,
    boundingBox: null,
  },
];

describe("BoundingBoxOverlay", () => {
  it("desenha um polígono só para os campos com geometria na página, com as coordenadas tal como vieram", () => {
    const { container } = render(<BoundingBoxOverlay fields={fields} page={1} activeField={null} />);

    const polygons = container.querySelectorAll("polygon");
    expect(polygons).toHaveLength(2);

    const supplierPolygon = container.querySelector('polygon[data-field="SUPPLIER_NAME"]');
    expect(supplierPolygon?.getAttribute("points")).toBe("0.1,0.2 0.4,0.2 0.4,0.3 0.1,0.3");
  });

  it("destaca o campo ativo com uma classe diferente da dos restantes", () => {
    const { container } = render(<BoundingBoxOverlay fields={fields} page={1} activeField="TOTAL_AMOUNT" />);

    const active = container.querySelector('polygon[data-field="TOTAL_AMOUNT"]');
    const inactive = container.querySelector('polygon[data-field="SUPPLIER_NAME"]');

    expect(active?.getAttribute("class")).not.toBe(inactive?.getAttribute("class"));
  });
});
