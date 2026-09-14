package com.docgrid.extraction;

import java.util.Objects;

/**
 * O que o motor de extração tem para dizer sobre si, na linguagem do health check.
 *
 * @param engine o nome do motor ({@code stub} em local/testes, {@code textract} em AWS)
 * @param ready se o motor está operacional, do ponto de vista dele
 * @param detail o que dizer ao humano que lê o {@code /actuator/health}
 */
public record ExtractorStatus(String engine, boolean ready, String detail) {

    public ExtractorStatus {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(detail, "detail");
    }
}
