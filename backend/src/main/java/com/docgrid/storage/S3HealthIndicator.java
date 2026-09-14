package com.docgrid.storage;

import java.time.Duration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * O estado do S3, do ponto de vista de quem lhe fala: o bucket existe e responde.
 *
 * <p>Timeout curto de propósito: sem {@code apiCallTimeout}, o SDK tenta três vezes com
 * backoff e o {@code /actuator/health} fica pendurado ~30 s com o serviço em baixo —
 * a sonda tem de responder depressa, e é ela que diz onde está a falha, não o silêncio.
 */
@Component
class S3HealthIndicator implements HealthIndicator {

    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(3);

    private final S3Client s3;
    private final StorageProperties props;

    S3HealthIndicator(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public Health health() {
        try {
            s3.headBucket(b -> b.bucket(props.bucket()).overrideConfiguration(o -> o.apiCallTimeout(API_CALL_TIMEOUT)));
            return Health.up().withDetail("bucket", props.bucket()).build();
        } catch (SdkException e) {
            return Health.down(e).withDetail("bucket", props.bucket()).build();
        }
    }
}
