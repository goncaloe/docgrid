package com.docgrid.document;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.docgrid.extraction.FieldGeometry;
import com.docgrid.shared.BaseEntity;

/**
 * Um campo lido de um documento, com o que se sabe sobre a fiabilidade do que se leu.
 *
 * <p>Uma linha por campo, e não uma coluna por campo, precisamente para caber aqui a
 * confiança e a origem. A regra 6 do {@code AGENTS.md} exige que essa informação chegue
 * até à interface, e ela só chega se existir desde o princípio.
 *
 * <p>Um campo lido pela máquina traz sempre confiança; um campo escrito por uma pessoa
 * nunca traz — a origem {@code HUMAN} já diz que não há incerteza a declarar. Os dois
 * construtores nomeados tornam isso impossível de trocar, e a constraint
 * {@code ck_extracted_fields_confidence_required} garante-o também na base de dados.
 */
@Entity
@Table(name = "extracted_fields")
class ExtractedField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, updatable = false, length = 40)
    private ExtractedFieldName fieldName;

    @Column(name = "value_text", length = 500)
    private String valueText;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private FieldSource source;

    /** A página onde o campo foi lido; só para leituras da máquina. */
    @Column(name = "page")
    private Integer page;

    /** O polígono que cerca o campo, como {@code [[x,y],...]} normalizado 0–1; só AI. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "jsonb")
    private String boundingBox;

    protected ExtractedField() {}

    private ExtractedField(
            Document document,
            ExtractedFieldName fieldName,
            String valueText,
            BigDecimal confidence,
            FieldSource source,
            Integer page,
            String boundingBox) {
        this.document = Objects.requireNonNull(document, "document");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        this.valueText = valueText;
        this.confidence = confidence;
        this.source = source;
        this.page = page;
        this.boundingBox = boundingBox;
    }

    /** Campo lido pelo motor de extração. A confiança e a geometria são obrigatórias. */
    static ExtractedField readByMachine(
            Document document,
            ExtractedFieldName fieldName,
            String valueText,
            BigDecimal confidence,
            FieldGeometry geometry) {
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(geometry, "geometry");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A confiança tem de estar entre 0 e 1, e não " + confidence);
        }
        ExtractedField field = new ExtractedField(
                document,
                fieldName,
                valueText,
                confidence,
                FieldSource.AI,
                geometry.page(),
                geometry.serializedPolygon());
        document.addExtractedField(field);
        return field;
    }

    /** Campo escrito por uma pessoa, na revisão manual. Sem confiança nem geometria. */
    static ExtractedField writtenByHuman(Document document, ExtractedFieldName fieldName, String valueText) {
        ExtractedField field = new ExtractedField(document, fieldName, valueText, null, FieldSource.HUMAN, null, null);
        document.addExtractedField(field);
        return field;
    }

    /**
     * Substitui o valor por um escrito à mão. O valor anterior não se perde: quem corrige
     * escreve também um evento {@link DocumentEventType#FIELD_CORRECTED} (etapa 06).
     */
    void correctTo(String newValue) {
        this.valueText = newValue;
        this.confidence = null;
        this.source = FieldSource.HUMAN;
        // O sítio onde a máquina leu já não descreve o que está aqui: quem corrigiu
        // escreveu um valor novo, sem coordenadas próprias.
        this.page = null;
        this.boundingBox = null;
    }

    Document getDocument() {
        return document;
    }

    ExtractedFieldName getFieldName() {
        return fieldName;
    }

    String getValueText() {
        return valueText;
    }

    BigDecimal getConfidence() {
        return confidence;
    }

    FieldSource getSource() {
        return source;
    }

    Integer getPage() {
        return page;
    }

    String getBoundingBox() {
        return boundingBox;
    }
}
