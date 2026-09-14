package com.docgrid.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/**
 * O indicador do motor de extração repete o que o motor declara: o stub em local devolve
 * UP com o caminho da fixture, para quem olha para o health saber o caso simulado.
 */
class ExtractorHealthIndicatorTest {

    @Test
    void stubReportsUpWithTheFixturePath() {
        ExtractionProperties props = new ExtractionProperties(
                "extraction/fixtures/clean-invoice.json",
                new ExtractionProperties.Textract("eu-west-1", "", "", "", java.time.Duration.ofSeconds(60)));
        ExtractorHealthIndicator indicator = new ExtractorHealthIndicator(new StubExtractor(props));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("engine")).isEqualTo("stub");
        assertThat(health.getDetails().get("detail")).asString().contains("clean-invoice.json");
    }
}
