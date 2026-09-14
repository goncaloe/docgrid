package com.docgrid.pipeline;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.docgrid.document.DocumentStatus;
import com.docgrid.pipeline.dto.PipelineStats;

/**
 * O estado do pipeline agora: quantos documentos em cada estado e quão cheias estão as
 * filas. A contagem lê-se por SQL sobre {@code documents} a partir deste pacote — o
 * precedente do ADR 0013 ("lê-se por SQL, escreve-se pelo pacote dono"), o mesmo do
 * dashboard; quem escreve em {@code documents} continua a ser {@code com.docgrid.document}.
 */
@Service
public class PipelineStatsService {

    /** Curto, de propósito: quem pergunta quer a resposta agora, e a fila em baixo é do health check. */
    private static final Duration SQS_TIMEOUT = Duration.ofSeconds(5);

    private final JdbcClient jdbc;
    private final SqsQueues queues;
    private final QueueProperties queueProperties;

    public PipelineStatsService(JdbcClient jdbc, SqsQueues queues, QueueProperties queueProperties) {
        this.jdbc = jdbc;
        this.queues = queues;
        this.queueProperties = queueProperties;
    }

    public PipelineStats current() {
        SqsQueues.QueueDepths depths = queues.depths(SQS_TIMEOUT);
        return new PipelineStats(
                documentsByStatus(),
                toQueue(queueProperties.name(), depths.main()),
                toQueue(queueProperties.dlqName(), depths.dlq()));
    }

    /** Contagem por estado; os oito estados de {@link DocumentStatus} estão sempre presentes. */
    Map<String, Long> documentsByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DocumentStatus status : DocumentStatus.values()) {
            counts.put(status.name(), 0L);
        }
        jdbc.sql("select status, count(*) as total from documents group by status")
                .query((rs, rowNum) -> Map.entry(rs.getString("status"), rs.getLong("total")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    private static PipelineStats.Queue toQueue(String name, SqsQueues.QueueDepth depth) {
        return new PipelineStats.Queue(name, depth.available(), depth.inFlight());
    }
}
