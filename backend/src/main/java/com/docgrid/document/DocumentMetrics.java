package com.docgrid.document;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * A taxa de automação como contadores com etiqueta: um documento extraído sem problemas
 * abre a contagem {@code review=none}; um que precisa de revisão humana,
 * {@code review=required}. Dois contadores dão o rácio em qualquer janela com
 * {@code rate()} — um gauge de rácio mentiria depois de um reinício. Ver
 * {@code docs/adr/0016-metricas-e-health-checks.md}.
 */
@Component
class DocumentMetrics {

    private final Counter requiresReview;
    private final Counter noReview;

    DocumentMetrics(MeterRegistry registry) {
        this.requiresReview = Counter.builder("docgrid.documents.extracted")
                .tag("review", "required")
                .register(registry);
        this.noReview = Counter.builder("docgrid.documents.extracted")
                .tag("review", "none")
                .register(registry);
    }

    void recordExtraction(boolean requiresReview) {
        (requiresReview ? this.requiresReview : noReview).increment();
    }
}
