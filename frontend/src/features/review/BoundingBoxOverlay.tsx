import type { ExtractedFieldName, ExtractedFieldResponse, PolygonPoint } from "../../api/types";
import styles from "./DocumentPreview.module.css";

interface BoundingBoxOverlayProps {
  fields: ExtractedFieldResponse[];
  page: number;
  activeField: ExtractedFieldName | null;
}

interface FieldWithGeometry extends ExtractedFieldResponse {
  boundingBox: PolygonPoint[];
}

function isOnPage(field: ExtractedFieldResponse, page: number): field is FieldWithGeometry {
  return field.page === page && field.boundingBox !== null;
}

/**
 * Sobrepõe um polígono por campo à página do documento. Sem conversão para pixels: as
 * coordenadas já vêm normalizadas 0–1, e o `viewBox` faz a escala sozinho, seja qual for
 * o tamanho a que a página é desenhada por baixo.
 */
export function BoundingBoxOverlay({ fields, page, activeField }: BoundingBoxOverlayProps) {
  const fieldsOnPage = fields.filter((field): field is FieldWithGeometry => isOnPage(field, page));

  return (
    <svg className={styles.overlay} viewBox="0 0 1 1" preserveAspectRatio="none">
      {fieldsOnPage.map((field) => (
        <polygon
          key={field.fieldName}
          data-field={field.fieldName}
          points={field.boundingBox.map((point) => `${String(point.x)},${String(point.y)}`).join(" ")}
          className={field.fieldName === activeField ? `${styles.polygon} ${styles.active}` : styles.polygon}
        />
      ))}
    </svg>
  );
}
