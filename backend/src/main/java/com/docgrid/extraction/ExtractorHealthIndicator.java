package com.docgrid.extraction;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * O estado do motor de extração na linguagem do actuator: traduz o
 * {@link ExtractorStatus} que o motor declara. É o actuator a depender da extração,
 * nunca o contrário — quem diz o que está saudável no seu domínio é cada pacote.
 */
@Component
class ExtractorHealthIndicator implements HealthIndicator {

    private final DocumentExtractor extractor;

    ExtractorHealthIndicator(DocumentExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public Health health() {
        ExtractorStatus status = extractor.status();
        Health.Builder builder = status.ready() ? Health.up() : Health.down();
        return builder.withDetail("engine", status.engine())
                .withDetail("detail", status.detail())
                .build();
    }
}
