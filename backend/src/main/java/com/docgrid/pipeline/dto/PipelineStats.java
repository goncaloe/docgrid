package com.docgrid.pipeline.dto;

import java.util.Map;

/**
 * O estado do pipeline num instante, da mesma fonte que alimenta os gauges: a contagem
 * de documentos por estado e a profundidade das filas. É o que o dashboard de
 * administração vai consumir (etapa 12).
 *
 * @param documentsByStatus as oito entradas de {@code DocumentStatus} presentes, zero
 *     incluído — um estado sem documentos não desaparece da resposta
 * @param main a fila principal
 * @param dlq a dead-letter queue
 */
public record PipelineStats(Map<String, Long> documentsByStatus, Queue main, Queue dlq) {

    public record Queue(String name, int available, int inFlight) {}
}
