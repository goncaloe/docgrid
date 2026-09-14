package com.docgrid.extraction;

/**
 * O motor que lê os campos de uma fatura.
 *
 * <p>Extração atrás de uma interface, de propósito (ver {@code docs/02-ARCHITECTURE.md}):
 * {@link StubExtractor} devolve dados fixos e serve o desenvolvimento local e os testes;
 * a etapa 04 acrescenta a implementação sobre o Textract, que só existe em AWS. Quem
 * consome a interface — o worker, na etapa 03 — não sabe qual das duas está a correr.
 */
public interface DocumentExtractor {

    /**
     * Extrai os campos de um documento. A etapa 03 corre-a com o {@link StubExtractor};
     * a etapa 04 acrescenta a versão sobre o Textract, e é aí que nasce a distinção
     * entre um documento ilegível (erro permanente) e um serviço indisponível
     * (erro transitório).
     */
    ExtractionResult extract(byte[] content, String contentType);

    /**
     * O estado do motor, para o health check — é o actuator a depender da extração,
     * nunca o contrário. Sem implementação por omissão de propósito: as duas
     * implementações têm cada uma o que dizer.
     */
    ExtractorStatus status();
}
