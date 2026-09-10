package com.docgrid.extraction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.textract.model.AnalyzeExpenseResponse;
import software.amazon.awssdk.services.textract.model.BoundingBox;
import software.amazon.awssdk.services.textract.model.ExpenseDetection;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.ExpenseType;
import software.amazon.awssdk.services.textract.model.Geometry;
import software.amazon.awssdk.services.textract.model.Point;

/**
 * Respostas gravadas do {@code AnalyzeExpense}, em JSON, levadas ao tipo real do SDK.
 *
 * <p>O {@code JsonProtocolUnmarshaller} do SDK é interno e acoplado ao protocolo; o
 * caminho daqui ao objeto real passa por um espelho mínimo do formato de resposta — os
 * nomes dos campos são os que chegam no fio, de propósito — mapeado para os builders do
 * SDK. O normalizador recebe {@link AnalyzeExpenseResponse} de verdade, como em
 * produção; o que está a fingir é só o serviço.
 *
 * <p>As fixtures estão em {@code src/test/resources/textract/}.
 */
final class TextractFixtures {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TextractFixtures() {}

    static AnalyzeExpenseResponse load(String fixturePath) {
        try {
            Response fixture = JSON.readValue(new ClassPathResource(fixturePath).getInputStream(), Response.class);
            return AnalyzeExpenseResponse.builder()
                    .expenseDocuments(fixture.expenseDocuments.stream()
                            .map(TextractFixtures::document)
                            .toList())
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("A resposta gravada " + fixturePath + " não existe ou não é válida", e);
        }
    }

    private static ExpenseDocument document(Document document) {
        return ExpenseDocument.builder()
                .expenseIndex(document.expenseIndex)
                .summaryFields(document.summaryFields.stream()
                        .map(TextractFixtures::summaryField)
                        .toList())
                .build();
    }

    private static ExpenseField summaryField(SummaryField field) {
        return ExpenseField.builder()
                .type(ExpenseType.builder()
                        .text(field.type.text)
                        .confidence(field.type.confidence)
                        .build())
                .valueDetection(ExpenseDetection.builder()
                        .text(field.valueDetection.text)
                        .confidence(field.valueDetection.confidence)
                        .geometry(Geometry.builder()
                                .boundingBox(BoundingBox.builder()
                                        .width(field.valueDetection.geometry.boundingBox.width)
                                        .height(field.valueDetection.geometry.boundingBox.height)
                                        .left(field.valueDetection.geometry.boundingBox.left)
                                        .top(field.valueDetection.geometry.boundingBox.top)
                                        .build())
                                .polygon(field.valueDetection.geometry.polygon.stream()
                                        .map(point -> Point.builder()
                                                .x(point.x)
                                                .y(point.y)
                                                .build())
                                        .toList())
                                .build())
                        .build())
                .pageNumber(field.pageNumber)
                .build();
    }

    /**
     * O espelho do formato de resposta, em camelCase Java com as anotações que apontam
     * para os nomes do fio — a fixture fica idêntica ao que o Textract devolve.
     */
    static final class Response {
        @com.fasterxml.jackson.annotation.JsonProperty("ExpenseDocuments")
        public List<Document> expenseDocuments;
    }

    static final class Document {
        @com.fasterxml.jackson.annotation.JsonProperty("ExpenseIndex")
        public int expenseIndex;

        @com.fasterxml.jackson.annotation.JsonProperty("SummaryFields")
        public List<SummaryField> summaryFields;
    }

    static final class SummaryField {
        @com.fasterxml.jackson.annotation.JsonProperty("Type")
        public Kind type;

        @com.fasterxml.jackson.annotation.JsonProperty("ValueDetection")
        public Detection valueDetection;

        @com.fasterxml.jackson.annotation.JsonProperty("PageNumber")
        public int pageNumber;
    }

    static final class Kind {
        @com.fasterxml.jackson.annotation.JsonProperty("Text")
        public String text;

        @com.fasterxml.jackson.annotation.JsonProperty("Confidence")
        public Float confidence;
    }

    static final class Detection {
        @com.fasterxml.jackson.annotation.JsonProperty("Text")
        public String text;

        @com.fasterxml.jackson.annotation.JsonProperty("Confidence")
        public Float confidence;

        @com.fasterxml.jackson.annotation.JsonProperty("Geometry")
        public Box geometry;
    }

    static final class Box {
        @com.fasterxml.jackson.annotation.JsonProperty("BoundingBox")
        public Frame boundingBox;

        @com.fasterxml.jackson.annotation.JsonProperty("Polygon")
        public List<Pt> polygon;
    }

    static final class Frame {
        @com.fasterxml.jackson.annotation.JsonProperty("Width")
        public Float width;

        @com.fasterxml.jackson.annotation.JsonProperty("Height")
        public Float height;

        @com.fasterxml.jackson.annotation.JsonProperty("Left")
        public Float left;

        @com.fasterxml.jackson.annotation.JsonProperty("Top")
        public Float top;
    }

    static final class Pt {
        @com.fasterxml.jackson.annotation.JsonProperty("X")
        public Float x;

        @com.fasterxml.jackson.annotation.JsonProperty("Y")
        public Float y;
    }
}
