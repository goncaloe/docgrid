package com.docgrid.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import com.docgrid.document.DocumentProcessor;
import com.docgrid.pipeline.dto.DlqMessageView;
import com.docgrid.pipeline.dto.RedriveSummary;

/**
 * Inspecionar e reprocessar a dead-letter queue.
 *
 * <p>Uma mensagem só chega à DLQ depois de o worker esgotar as tentativas (erro
 * transitório que nunca passou) ou de a recusar de imediato (evento ilegível, objeto sem
 * documento). Este serviço é a forma de alguém olhar para o que ficou preso e decidir:
 * corrigir a causa e mandar reprocessar, ou deixar a mensagem morrer na DLQ.
 *
 * <p>Espreitar não consome: a listagem recebe as mensagens com uma visibilidade curta e
 * não as apaga, portanto elas voltam a ficar visíveis sozinhas. Reprocessar, sim: copia o
 * corpo para a fila principal e só então apaga da DLQ — se algo falhar a meio, a mensagem
 * fica na DLQ e tenta-se outra vez.
 */
@Service
public class DlqAdmin {

    private static final Logger log = LoggerFactory.getLogger(DlqAdmin.class);

    /** Um teto para não varrer uma DLQ descontrolada num só pedido. */
    private static final int MAX_MESSAGES_PER_CALL = 100;

    // Espreitar não esconde: a mensagem fica logo visível para a próxima receção.
    private static final int PEEK_VISIBILITY_SECONDS = 0;
    private static final int REDRIVE_VISIBILITY_SECONDS = 30;
    private static final int PREVIEW_LENGTH = 200;

    private final SqsQueues queues;
    private final S3EventNotificationParser parser;
    private final DocumentProcessor processor;

    public DlqAdmin(SqsQueues queues, S3EventNotificationParser parser, DocumentProcessor processor) {
        this.queues = queues;
        this.parser = parser;
        this.processor = processor;
    }

    /** O que está parado na DLQ agora. As mensagens continuam lá depois desta chamada. */
    public List<DlqMessageView> list() {
        Map<String, DlqMessageView> byId = new LinkedHashMap<>();
        List<Message> batch;
        while (byId.size() < MAX_MESSAGES_PER_CALL
                && !(batch = queues.receiveFromDlq(PEEK_VISIBILITY_SECONDS)).isEmpty()) {
            boolean sawSomethingNew = false;
            for (Message message : batch) {
                if (byId.putIfAbsent(message.messageId(), toView(message)) == null) {
                    sawSomethingNew = true;
                }
            }
            if (!sawSomethingNew) {
                break;
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * Devolve à fila principal todas as mensagens da DLQ, para uma nova tentativa. Só faz
     * sentido depois de a causa da falha estar tratada — senão elas voltam a cair aqui.
     *
     * <p>Cada documento {@code FAILED} que uma mensagem refira é reaberto para
     * {@code PROCESSING} antes de a mensagem voltar à fila: sem isso, a entrega seguinte
     * via o documento já {@code FAILED} e deixava a mensagem seguir outra vez para a DLQ —
     * o reprocessamento não seria reprocessamento nenhum.
     */
    public RedriveSummary redriveAll() {
        int moved = 0;
        List<Message> batch;
        while (moved < MAX_MESSAGES_PER_CALL
                && !(batch = queues.receiveFromDlq(REDRIVE_VISIBILITY_SECONDS)).isEmpty()) {
            for (Message message : batch) {
                reopenReferencedDocuments(message);
                queues.sendToMain(message.body());
                queues.deleteFromDlq(message.receiptHandle());
                moved++;
            }
        }
        log.info("Reprocessamento da DLQ: {} mensagens devolvidas à fila principal", moved);
        return new RedriveSummary(moved);
    }

    private void reopenReferencedDocuments(Message message) {
        List<S3ObjectReference> references;
        try {
            references = parser.parse(message.body());
        } catch (MalformedS3EventException ilegivel) {
            // Uma mensagem ilegível não refere documento nenhum. Volta à fila na mesma —
            // vai falhar o parse e regressar à DLQ, mas essa decisão é de quem a inspeciona.
            return;
        }
        references.forEach(reference -> processor.reopenForReprocessing(reference.key()));
    }

    private DlqMessageView toView(Message message) {
        int receiveCount = receiveCount(message);
        try {
            List<S3ObjectReference> references = parser.parse(message.body());
            S3ObjectReference object = references.isEmpty() ? null : references.get(0);
            return new DlqMessageView(
                    message.messageId(),
                    object == null ? null : object.bucket(),
                    object == null ? null : object.key(),
                    receiveCount,
                    null);
        } catch (MalformedS3EventException ilegivel) {
            return new DlqMessageView(message.messageId(), null, null, receiveCount, preview(message.body()));
        }
    }

    private static int receiveCount(Message message) {
        String count = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        return count == null ? 0 : Integer.parseInt(count);
    }

    private static String preview(String body) {
        if (body == null || body.isBlank()) {
            return "(vazio)";
        }
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "…";
    }
}
