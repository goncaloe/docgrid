package com.docgrid.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.docgrid.pipeline.dto.PipelineStats;

/**
 * Os gauges escrevem na registry o que o serviço de estatísticas diz, e o alerta da
 * DLQ não é ruído: um aviso por mudança de contagem, não um por ronda.
 */
class PipelineMetricsTest {

    private static final Logger metricsLogger = (Logger) LoggerFactory.getLogger(PipelineMetrics.class);

    @Test
    void refreshPublishesDocumentsByStatusAndQueueDepth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineStatsService stats = mock(PipelineStatsService.class);
        when(stats.current())
                .thenReturn(new PipelineStats(
                        Map.of("UPLOADED", 3L, "NEEDS_REVIEW", 1L),
                        new PipelineStats.Queue("main", 2, 1),
                        new PipelineStats.Queue("dlq", 4, 0)));

        PipelineMetrics metrics = new PipelineMetrics(registry, stats);
        metrics.refresh();

        assertThat(registry.get("docgrid.documents.count")
                        .tag("status", "UPLOADED")
                        .gauge()
                        .value())
                .isEqualTo(3.0);
        assertThat(registry.get("docgrid.documents.count")
                        .tag("status", "NEEDS_REVIEW")
                        .gauge()
                        .value())
                .isEqualTo(1.0);
        assertThat(registry.get("docgrid.queue.depth")
                        .tag("queue", "main")
                        .gauge()
                        .value())
                .isEqualTo(2.0);
        assertThat(registry.get("docgrid.queue.in_flight")
                        .tag("queue", "dlq")
                        .gauge()
                        .value())
                .isZero();
    }

    @Test
    void dlqWarningIsNotRepeatedWhileTheCountIsUnchanged() {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        metricsLogger.addAppender(logged);
        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            PipelineStatsService stats = mock(PipelineStatsService.class);
            when(stats.current())
                    .thenReturn(new PipelineStats(
                            Map.of(), new PipelineStats.Queue("main", 0, 0), new PipelineStats.Queue("dlq", 4, 0)));

            PipelineMetrics metrics = new PipelineMetrics(registry, stats);
            metrics.refresh();
            metrics.refresh();

            assertThat(logged.list)
                    .filteredOn(event -> "WARN".equals(event.getLevel().toString()))
                    .hasSize(1);
        } finally {
            metricsLogger.detachAppender(logged);
            logged.stop();
        }
    }

    @Test
    void dlqBackToZeroIsReportedAsInfo() {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        metricsLogger.addAppender(logged);
        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            PipelineStatsService stats = mock(PipelineStatsService.class);
            when(stats.current())
                    .thenReturn(new PipelineStats(
                            Map.of(), new PipelineStats.Queue("main", 0, 0), new PipelineStats.Queue("dlq", 1, 0)))
                    .thenReturn(new PipelineStats(
                            Map.of(), new PipelineStats.Queue("main", 0, 0), new PipelineStats.Queue("dlq", 0, 0)));

            PipelineMetrics metrics = new PipelineMetrics(registry, stats);
            metrics.refresh();
            metrics.refresh();

            assertThat(logged.list)
                    .filteredOn(event -> "INFO".equals(event.getLevel().toString()))
                    .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("voltou a zero"));
        } finally {
            metricsLogger.detachAppender(logged);
            logged.stop();
        }
    }
}
