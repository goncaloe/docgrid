package com.docgrid.demo;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * O catálogo como bean: gerado uma vez no arranque e partilhado por quem o escreve (o
 * seed) e por quem o lê (o {@code DemoExtractor}).
 *
 * <p>Tem de ser o mesmo objeto para os dois: se cada um gerasse o seu, uma diferença de
 * data entre eles bastava para o extractor devolver valores de outra fatura.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
class DemoConfiguration {

    @Bean
    DemoInvoiceCatalog demoInvoiceCatalog(DemoProperties properties, Clock clock) {
        return new DemoInvoiceCatalog(properties.count(), LocalDate.now(clock));
    }
}
