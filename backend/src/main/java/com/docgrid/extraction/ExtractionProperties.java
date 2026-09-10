package com.docgrid.extraction;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do motor de extração: que fatura o stub devolve e como chegar ao Textract.
 *
 * <p>O mesmo padrão de {@code docgrid.queue} e {@code docgrid.storage}: com
 * {@code endpoint} definido aponta-se ao LocalStack com credenciais estáticas; sem ele,
 * à AWS com a cadeia de credenciais por omissão. No caso do Textract, em local e nos
 * testes o cliente nem sequer existe — o stub cobre esses perfis (LocalStack não tem
 * Textract).
 *
 * @param stubFixture a fixture JSON que o {@link StubExtractor} devolve, no classpath;
 *     trocar o caminho troca o caso que o stub simula, sem tocar em código
 * @param textract os parâmetros de ligação ao serviço
 */
@ConfigurationProperties("docgrid.extraction")
public record ExtractionProperties(String stubFixture, Textract textract) {

    /**
     * A ligação ao Textract.
     *
     * @param region a região do serviço
     * @param endpoint presente só em testes manuais contra um recetor próprio; vazio ⇒ AWS
     * @param accessKey credenciais estáticas, só com {@code endpoint}; sem ele a cadeia por
     *     omissão decide
     * @param secretKey par de {@code accessKey}
     * @param apiTimeout o timeout de cada chamada ao serviço: uma análise que estoura isto
     *     falha como erro transitório e o SQS volta a tentar
     */
    public record Textract(String region, String endpoint, String accessKey, String secretKey, Duration apiTimeout) {

        public boolean hasCustomEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }
    }
}
