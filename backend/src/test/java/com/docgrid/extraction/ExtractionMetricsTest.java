package com.docgrid.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import com.docgrid.document.InvoiceFields;

/**
 * A duração da extração conta por desfecho — e a métrica não engole a falha: uma
 * extração ilegível é medida como tal e relançada, porque quem decide o destino do
 * documento é o pipeline, não a métrica.
 */
class ExtractionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ExtractionMetrics metrics = new ExtractionMetrics(registry);

    @Test
    void aSuccessfulExtractionCountsAsSuccess() {
        metrics.time(() -> new ExtractionResult(emptyFields(), Map.of(), Map.of()));

        assertThat(timerValue("success")).isGreaterThan(0);
    }

    @Test
    void anUnreadableDocumentCountsAsUnreadableAndIsRethrown() {
        assertThatThrownBy(() -> metrics.time(() -> {
                    throw new UnreadableDocumentException("foto esborratada");
                }))
                .isInstanceOf(UnreadableDocumentException.class);

        assertThat(timerValue("unreadable")).isGreaterThan(0);
    }

    private double timerValue(String outcome) {
        return registry.get("docgrid.extraction.duration")
                .tag("outcome", outcome)
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private static InvoiceFields emptyFields() {
        return new InvoiceFields(null, null, null, null, null, null, null, null);
    }
}
