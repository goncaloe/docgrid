package com.docgrid.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/**
 * O indicador de saúde do SQS com o serviço inacessível: cliente montado como a
 * aplicação o monta ({@link SqsConfig}), apontado a uma porta onde nada responde.
 */
class SqsHealthIndicatorTest {

    @Test
    void reportsDownWhenSqsIsUnreachable() {
        QueueProperties props = new QueueProperties(
                "docgrid-document-processing",
                "docgrid-document-processing-dlq",
                "eu-west-1",
                "http://localhost:1",
                "test",
                "test",
                3,
                Duration.ofSeconds(1));
        SqsQueues queues = new SqsQueues(SqsConfig.buildSqsClient(props), props);
        SqsHealthIndicator indicator = new SqsHealthIndicator(queues, props);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("queues"))
                .isEqualTo("docgrid-document-processing, docgrid-document-processing-dlq");
        assertThat(health.getDetails().get("error")).isNotNull();
    }
}
