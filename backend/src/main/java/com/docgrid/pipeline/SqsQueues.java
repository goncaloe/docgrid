package com.docgrid.pipeline;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import com.docgrid.shared.Correlation;

/**
 * As operações do SQS de que o pipeline precisa, por cima do {@link SqsClient} cru.
 *
 * <p>Os URLs resolvem-se preguiçosamente e ficam em cache: as filas são criadas pela
 * infraestrutura antes de a aplicação arrancar ({@code docker compose} em local,
 * Terraform em AWS), mas o momento em que o primeiro componente precisa delas já vem
 * depois do arranque do contexto — e falhar cedo com um pedido vazio não compra nada.
 */
@Component
public class SqsQueues {

    private static final int MAX_MESSAGES_PER_RECEIVE = 10;

    private final SqsClient sqs;
    private final QueueProperties props;

    private volatile String mainQueueUrl;
    private volatile String dlqQueueUrl;

    public SqsQueues(SqsClient sqs, QueueProperties props) {
        this.sqs = sqs;
        this.props = props;
    }

    /**
     * Recebe da fila principal, com long polling (o {@code waitTime} da configuração) e
     * todos os atributos — é o {@code ApproximateReceiveCount} que diz ao worker se esta
     * é a última entrega antes da DLQ.
     */
    public List<Message> receiveFromMain() {
        return sqs.receiveMessage(r -> r.queueUrl(mainUrl())
                        .maxNumberOfMessages(MAX_MESSAGES_PER_RECEIVE)
                        .waitTimeSeconds((int) props.waitTime().toSeconds())
                        // Os de sistema (ApproximateReceiveCount) e os de utilizador — o
                        // X-Correlation-Id que enviamos no redrive — vêm os dois.
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                        .messageAttributeNames("All"))
                .messages();
    }

    /** Apaga uma mensagem da fila principal: o processamento dela acabou bem. */
    public void deleteFromMain(String receiptHandle) {
        sqs.deleteMessage(r -> r.queueUrl(mainUrl()).receiptHandle(receiptHandle));
    }

    /**
     * Recebe da DLQ. {@code visibilitySeconds} controla a janela de trabalho: 0 para
     * espreitar (a mensagem fica imediatamente disponível para a próxima receção), uns
     * segundos para a mover — se quem a move cair a meio, ela volta sozinha.
     */
    public List<Message> receiveFromDlq(int visibilitySeconds) {
        return sqs.receiveMessage(r -> r.queueUrl(dlqUrl())
                        .maxNumberOfMessages(MAX_MESSAGES_PER_RECEIVE)
                        .visibilityTimeout(visibilitySeconds)
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL))
                .messages();
    }

    public void deleteFromDlq(String receiptHandle) {
        sqs.deleteMessage(r -> r.queueUrl(dlqUrl()).receiptHandle(receiptHandle));
    }

    /** Devolve uma mensagem à fila principal — o reprocessamento a partir da DLQ. */
    public void sendToMain(String body) {
        var request = SendMessageRequest.builder().queueUrl(mainUrl()).messageBody(body);
        String correlationId = Correlation.current();
        if (correlationId != null) {
            request.messageAttributes(Map.of(
                    Correlation.SQS_ATTRIBUTE,
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(correlationId)
                            .build()));
        }
        sqs.sendMessage(request.build());
    }

    private String mainUrl() {
        String url = mainQueueUrl;
        if (url == null) {
            url = sqs.getQueueUrl(r -> r.queueName(props.name())).queueUrl();
            mainQueueUrl = url;
        }
        return url;
    }

    private String dlqUrl() {
        String url = dlqQueueUrl;
        if (url == null) {
            url = sqs.getQueueUrl(r -> r.queueName(props.dlqName())).queueUrl();
            dlqQueueUrl = url;
        }
        return url;
    }
}
