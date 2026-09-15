package com.docgrid.demo;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.docgrid.extraction.DocumentExtractor;
import com.docgrid.extraction.ExtractionResult;
import com.docgrid.extraction.ExtractorStatus;

/**
 * A extração do perfil {@code demo}: lê o marcador do PDF e devolve os valores da fatura
 * que o catálogo guarda para ele.
 *
 * <p>O {@code StubExtractor} devolve sempre a mesma fixture — é o que serve o
 * desenvolvimento e os testes, e não se lhe toca: é código de produção de que três etapas
 * dependem. Para a demonstração precisar de sessenta faturas diferentes basta esta
 * implementação, {@link Primary} e só no perfil {@code demo}, ao lado dele. Quem consome a
 * interface continua sem saber qual das três está a correr, que é o ponto de a extração
 * estar atrás de uma interface.
 *
 * <p>Um PDF sem marcador — alguém que arrasta um ficheiro seu para a aplicação semeada —
 * recebe a primeira fatura do catálogo. É deliberado: a alternativa era falhar, e um
 * documento em {@code FAILED} no meio de uma demonstração explica-se pior do que um
 * documento com os valores errados.
 */
@Component
@Profile("demo")
@Primary
class DemoExtractor implements DocumentExtractor {

    private final DemoInvoiceCatalog catalog;

    DemoExtractor(DemoInvoiceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public ExtractionResult extract(byte[] content, String contentType) {
        return InvoicePdfWriter.markerOf(content)
                .flatMap(catalog::findBySlug)
                .orElseGet(catalog::first)
                .asExtractionResult();
    }

    @Override
    public ExtractorStatus status() {
        return new ExtractorStatus(
                "demo",
                true,
                "catálogo de demonstração: %d faturas"
                        .formatted(catalog.invoices().size()));
    }
}
