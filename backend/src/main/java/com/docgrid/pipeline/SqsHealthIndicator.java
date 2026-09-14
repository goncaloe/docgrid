package com.docgrid.pipeline;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * O estado do SQS, do ponto de vista de quem lhe fala: as duas filas existem e
 * respondem, e quantas mensagens têm em cada contador. A profundidade da DLQ não
 * derruba o health — uma mensagem presa é problema de negócio (o alerta está nas
 * métricas), não de saúde do processo; ver {@code docs/adr/0016-metricas-e-health-checks.md}.
 *
 * <p>Timeout curto de propósito, como no {@code S3HealthIndicator}: a sonda responde
 * depressa e diz onde está a falha.
 */
@Component
class SqsHealthIndicator implements HealthIndicator {

    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(3);

    private final SqsQueues queues;
    private final QueueProperties props;

    SqsHealthIndicator(SqsQueues queues, QueueProperties props) {
        this.queues = queues;
        this.props = props;
    }

    @Override
    public Health health() {
        try {
            SqsQueues.QueueDepths depths = queues.depths(API_CALL_TIMEOUT);
            return Health.up()
                    .withDetail(props.name(), depth(depths.main()))
                    .withDetail(props.dlqName(), depth(depths.dlq()))
                    .build();
        } catch (SdkException e) {
            return Health.down(e)
                    .withDetail("queues", props.name() + ", " + props.dlqName())
                    .build();
        }
    }

    private static Map<String, Integer> depth(SqsQueues.QueueDepth depth) {
        return Map.of("available", depth.available(), "inFlight", depth.inFlight());
    }
}
