package com.docgrid.document.dto;

import java.math.BigDecimal;

/**
 * Um campo extraído, com o grau de confiança até à interface — regra 6 do
 * {@code AGENTS.md}. {@code confidence} é {@code null} para um campo corrigido à mão: a
 * origem {@code HUMAN} já diz que não há incerteza a declarar.
 */
public record ExtractedFieldResponse(String fieldName, String value, BigDecimal confidence, String source) {}
