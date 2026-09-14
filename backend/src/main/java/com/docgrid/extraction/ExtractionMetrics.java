package com.docgrid.extraction;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * A duração da extração, por desfecho — {@code success}, {@code unreadable} (ilegível,
 * decisão à primeira) ou {@code error} (qualquer outra falha). A métrica nunca muda o
 * comportamento: mede, regista e relança sempre a exceção.
 */
@Component
public class ExtractionMetrics {

    private static final String NAME = "docgrid.extraction.duration";

    private final MeterRegistry registry;

    public ExtractionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public ExtractionResult time(Supplier<ExtractionResult> extraction) {
        long start = System.nanoTime();
        try {
            ExtractionResult result = extraction.get();
            record("success", start);
            return result;
        } catch (UnreadableDocumentException e) {
            record("unreadable", start);
            throw e;
        } catch (RuntimeException e) {
            record("error", start);
            throw e;
        }
    }

    private void record(String outcome, long start) {
        Timer.builder(NAME)
                .tag("outcome", outcome)
                .register(registry)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
}
