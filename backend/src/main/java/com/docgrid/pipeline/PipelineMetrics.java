package com.docgrid.pipeline;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

import com.docgrid.pipeline.dto.PipelineStats;

/**
 * Os gauges de estado do pipeline, refrescados a cada 30 s da mesma fonte do endpoint
 * de estatísticas — documentos por estado e profundidade das filas — mais o alerta da
 * DLQ: um {@code WARN} quando a contagem muda, não um som a cada ronda (uma mensagem
 * presa é problema de negócio, não de saúde do processo — quem reporta a fila em baixo
 * é o health check). Ver {@code docs/adr/0016-metricas-e-health-checks.md}.
 */
@Component
class PipelineMetrics {

    private static final Logger log = LoggerFactory.getLogger(PipelineMetrics.class);

    private final PipelineStatsService stats;
    private final MultiGauge documents;

    private final AtomicInteger mainDepth = new AtomicInteger();
    private final AtomicInteger dlqDepth = new AtomicInteger();
    private final AtomicInteger mainInFlight = new AtomicInteger();
    private final AtomicInteger dlqInFlight = new AtomicInteger();

    private int lastDlqCount;

    PipelineMetrics(MeterRegistry registry, PipelineStatsService stats) {
        this.stats = stats;
        this.documents = MultiGauge.builder("docgrid.documents.count").register(registry);
        Gauge.builder("docgrid.queue.depth", mainDepth, AtomicInteger::get)
                .tag("queue", "main")
                .register(registry);
        Gauge.builder("docgrid.queue.depth", dlqDepth, AtomicInteger::get)
                .tag("queue", "dlq")
                .register(registry);
        Gauge.builder("docgrid.queue.in_flight", mainInFlight, AtomicInteger::get)
                .tag("queue", "main")
                .register(registry);
        Gauge.builder("docgrid.queue.in_flight", dlqInFlight, AtomicInteger::get)
                .tag("queue", "dlq")
                .register(registry);
    }

    @Scheduled(fixedRate = 30_000)
    void refresh() {
        try {
            PipelineStats current = stats.current();
            documents.register(
                    current.documentsByStatus().entrySet().stream()
                            .map(entry -> MultiGauge.Row.of(Tags.of("status", entry.getKey()), entry.getValue()))
                            .toList(),
                    // Substitui as linhas da ronda anterior: sem overwrite, cada ronda
                    // acrescentava tags novas e a cardinalidade crescia sem limite.
                    true);
            mainDepth.set(current.main().available());
            mainInFlight.set(current.main().inFlight());
            dlqDepth.set(current.dlq().available());
            dlqInFlight.set(current.dlq().inFlight());
            alertOnDlq(current.dlq().available());
        } catch (SdkException e) {
            // A fila em baixo é do health check; os gauges ficam no último valor
            // conhecido. Um aviso a cada 30 s durante o desenvolvimento seria
            // insuportável.
            log.debug("SQS indisponível no refresh das métricas (os gauges ficam no último valor): {}", e.getMessage());
        }
    }

    private void alertOnDlq(int dlqAvailable) {
        if (dlqAvailable == lastDlqCount) {
            return;
        }
        if (dlqAvailable > 0) {
            log.warn("A DLQ tem {} mensagens paradas; ver /api/admin/dlq", dlqAvailable);
        } else {
            log.info("A DLQ voltou a zero");
        }
        lastDlqCount = dlqAvailable;
    }
}
