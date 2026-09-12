package com.docgrid.document.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um campo extraído, com o grau de confiança até à interface — regra 6 do
 * {@code AGENTS.md}. {@code confidence} é {@code null} para um campo corrigido à mão: a
 * origem {@code HUMAN} já diz que não há incerteza a declarar. {@code page} e
 * {@code boundingBox} seguem a mesma regra — só um campo lido pela máquina tem onde
 * apontar na página.
 */
public record ExtractedFieldResponse(
        String fieldName,
        String value,
        BigDecimal confidence,
        String source,
        Integer page,
        List<PolygonPointResponse> boundingBox) {}
