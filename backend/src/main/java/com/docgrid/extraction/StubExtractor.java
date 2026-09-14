package com.docgrid.extraction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;

/**
 * A extração que devolve o que um ficheiro JSON de fixture diz, e nada mais.
 *
 * <p>É o que corre em local e nos testes: o pipeline inteiro — evento, fila, worker,
 * estados, idempotência, geometria — exercita-se a sério sem uma chamada à AWS. Trocar o
 * perfil troca a implementação; trocar {@code docgrid.extraction.stub-fixture} troca o
 * caso simulado — fatura limpa, foto tremida, campo em falta — sem tocar em código.
 *
 * <p>A fixture carrega-se logo no arranque e o resultado guarda-se: um caminho errado é
 * um erro de configuração, não um caso de runtime — falha cedo, na subida.
 *
 * <p>Os valores das fixtures batem certo (aritmética, taxa de IVA, NIF válido): um stub
 * que viola as regras de negócio que a etapa 05 vai impor obrigaria os testes a
 * distinguir entre "o pipeline está errado" e "os dados de exemplo estão".
 */
@Component
@Profile({"local", "test"})
public class StubExtractor implements DocumentExtractor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExtractionResult result;
    private final String fixturePath;

    public StubExtractor(ExtractionProperties properties) {
        this.fixturePath = properties.stubFixture();
        this.result = loadFixture(fixturePath);
    }

    @Override
    public ExtractionResult extract(byte[] content, String contentType) {
        return result;
    }

    @Override
    public ExtractorStatus status() {
        // Se o bean existe, a fixture carregou (erro de configuração falha o arranque);
        // o detalhe diz qual é, para quem olha para o health saber o caso simulado.
        return new ExtractorStatus("stub", true, "fixture: " + fixturePath);
    }

    private static ExtractionResult loadFixture(String fixturePath) {
        FixtureDocument fixture;
        try {
            fixture = JSON.readValue(new ClassPathResource(fixturePath).getInputStream(), FixtureDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException("A fixture " + fixturePath + " não existe ou não é válida", e);
        }
        return new ExtractionResult(fixture.invoiceFields(), fixture.confidences(), fixture.geometries());
    }

    /**
     * O formato da fixture. Uma entrada por campo extraído, na chave camelCase do
     * {@link InvoiceFields}; um campo em falta é uma entrada que simplesmente não está no
     * JSON — o caso "o motor não leu isto".
     */
    static final class FixtureDocument {
        public Map<String, FixtureField> fields = new HashMap<>();

        InvoiceFields invoiceFields() {
            return new InvoiceFields(
                    text("supplierName"),
                    text("supplierTaxId"),
                    text("invoiceNumber"),
                    date("issueDate"),
                    amount("netAmount"),
                    amount("vatAmount"),
                    amount("vatRate"),
                    amount("totalAmount"));
        }

        Map<ExtractedFieldName, BigDecimal> confidences() {
            Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(ExtractedFieldName.class);
            fields.forEach((name, field) -> confidences.put(name(name), field.confidence));
            return confidences;
        }

        Map<ExtractedFieldName, FieldGeometry> geometries() {
            Map<ExtractedFieldName, FieldGeometry> geometries = new EnumMap<>(ExtractedFieldName.class);
            fields.forEach((name, field) -> geometries.put(name(name), field.geometry()));
            return geometries;
        }

        private String text(String name) {
            FixtureField field = fields.get(name);
            return field == null ? null : field.value;
        }

        private LocalDate date(String name) {
            FixtureField field = fields.get(name);
            return field == null ? null : LocalDate.parse(field.value, DateTimeFormatter.ISO_LOCAL_DATE);
        }

        private BigDecimal amount(String name) {
            FixtureField field = fields.get(name);
            return field == null ? null : new BigDecimal(field.value).setScale(2, RoundingMode.HALF_UP);
        }

        /** {@code supplierName} → {@code SUPPLIER_NAME}: a chave é o campo, em camelCase. */
        private static ExtractedFieldName name(String key) {
            StringBuilder enumName = new StringBuilder();
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (Character.isUpperCase(c) && i > 0) {
                    enumName.append('_');
                }
                enumName.append(Character.toUpperCase(c));
            }
            return ExtractedFieldName.valueOf(enumName.toString());
        }
    }

    /**
     * Um campo na fixture: o valor, a confiança e o sítio da página. O {@code value} de
     * um montante é uma string — {@code "100.00"} — porque é assim que se escreve
     * honestamente um número decimal num formato de texto.
     */
    static final class FixtureField {
        public String value;
        public BigDecimal confidence;
        public int page;
        public double[][] polygon;

        FieldGeometry geometry() {
            FieldGeometry.Point[] points = new FieldGeometry.Point[polygon.length];
            for (int i = 0; i < polygon.length; i++) {
                points[i] = new FieldGeometry.Point(polygon[i][0], polygon[i][1]);
            }
            return new FieldGeometry(page, List.of(points));
        }
    }
}
