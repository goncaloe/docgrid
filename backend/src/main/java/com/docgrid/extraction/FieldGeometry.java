package com.docgrid.extraction;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Onde um campo foi lido na página do documento: o polígono que o cerca, em coordenadas
 * normalizadas 0–1.
 *
 * <p>O Textract devolve a geometria já normalizada às dimensões da página — não há
 * dimensões para guardar nem para converter. O frontend (etapas 07/08) sobrepõe o
 * polígono à imagem ou ao PDF com uma multiplicação simples.
 *
 * @param page a página onde o campo está, a partir de 1
 * @param polygon os vértices do polígono, por ordem, em coordenadas normalizadas
 */
public record FieldGeometry(int page, List<Point> polygon) {

    public FieldGeometry {
        Objects.requireNonNull(polygon, "polygon");
        if (page < 1) {
            throw new IllegalArgumentException("A página tem de ser ≥ 1, e não " + page);
        }
        if (polygon.size() < 3) {
            throw new IllegalArgumentException("Um polígono tem de ter pelo menos 3 pontos, e não " + polygon.size());
        }
        polygon = List.copyOf(polygon);
        for (Point point : polygon) {
            Objects.requireNonNull(point, "point");
            if (point.x() < 0.0 || point.x() > 1.0 || point.y() < 0.0 || point.y() > 1.0) {
                throw new IllegalArgumentException("As coordenadas são normalizadas 0–1, e não " + point);
            }
        }
    }

    /**
     * O polígono no formato em que se guarda em {@code extracted_fields.bounding_box}:
     * um array JSON de pares {@code [x,y]} — por exemplo
     * {@code [[0.1,0.2],[0.4,0.2],[0.4,0.3],[0.1,0.3]]}.
     */
    public String serializedPolygon() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < polygon.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("[%s,%s]".formatted(polygon.get(i).x(), polygon.get(i).y()));
        }
        return json.append(']').toString();
    }

    /**
     * Um canto do polígono, em coordenadas normalizadas 0–1 (0,0 é o canto superior
     * esquerdo da página, como o Textract define).
     */
    public record Point(double x, double y) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "[%.4f,%.4f]", x, y);
        }
    }
}
