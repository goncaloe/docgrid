package com.docgrid.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/**
 * O indicador de saúde do S3 com o serviço inacessível: cliente montado exatamente como
 * a aplicação o monta ({@link StorageConfig}), apontado a uma porta onde nada responde.
 * Cobre o critério "parar o LocalStack põe o health a DOWN com a causa" sem parar
 * container nenhum.
 */
class S3HealthIndicatorTest {

    @Test
    void reportsDownWhenS3IsUnreachable() {
        StorageProperties props =
                new StorageProperties("docgrid-documents", "eu-west-1", "http://localhost:1", "test", "test");
        S3HealthIndicator indicator = new S3HealthIndicator(StorageConfig.buildS3Client(props), props);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("bucket")).isEqualTo("docgrid-documents");
        // A causa fica no detalhe "error" que Health.down(Throwable) acrescenta.
        assertThat(health.getDetails().get("error")).isNotNull();
    }
}
