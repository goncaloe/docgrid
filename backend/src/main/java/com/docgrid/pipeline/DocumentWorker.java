package com.docgrid.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import com.docgrid.document.DocumentProcessor;
import com.docgrid.document.ProcessingOutcome;
import com.docgrid.shared.Correlation;
import com.docgrid.shared.DomainException;

/**
 * O consumidor da fila: long polling num fio próprio, enquanto a aplicação viver.
 *
 * <p>Consumo à mão sobre o {@code SqsClient}, e não o {@code @SqsListener} do Spring
 * Cloud AWS: o que esta etapa quer demonstrar é precisamente a mecânica que o listener
 * esconde — visibilidade, entregas repetidas, {@code ApproximateReceiveCount}, o ack como
 * decisão — e ele traria uma dependência nova para a stack (ver
 * {@code docs/adr/0008-escolha-de-sqs-e-desenho-do-worker.md}). Viver só com o perfil
 * {@code worker}: a API e o worker partilham o código, não o processo.
 *
 * <p>Cada mensagem processa-se até ao fim antes da seguinte: um ficheiro grande à frente
 * de um pequeno não deixa o pequeno preso atrás dele, e a concorrência dentro do
 * processo é a etapa 04 que a decide, quando houver trabalho de verdade para paralelizar.
 */
@Component
@Profile("worker")
public class DocumentWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DocumentWorker.class);

    private static final Duration PAUSE_ON_QUEUE_UNAVAILABLE = Duration.ofSeconds(2);

    private final SqsQueues queues;
    private final DocumentProcessor processor;
    private final S3EventNotificationParser parser;
    private final QueueProperties props;

    private volatile boolean running;
    private ExecutorService executor;

    public DocumentWorker(
            SqsQueues queues, DocumentProcessor processor, S3EventNotificationParser parser, QueueProperties props) {
        this.queues = queues;
        this.processor = processor;
        this.parser = parser;
        this.props = props;
    }

    @Override
    public void start() {
        running = true;
        executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "document-worker"));
        executor.submit(this::poll);
        log.info("Worker à escuta da fila '{}'", props.name());
    }

    @Override
    public void stop() {
        running = false;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("O worker não parou a tempo; uma receção em curso será interrompida");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void poll() {
        while (running) {
            try {
                drainOnce();
            } catch (SdkClientException e) {
                // A fila não está acessível — não é mortal, e não há fila a que valha a
                // pena perguntar num ciclo apertado. Espera-se e tenta-se outra vez.
                log.warn("Fila indisponível, nova tentativa em {}: {}", PAUSE_ON_QUEUE_UNAVAILABLE, e.getMessage());
                pause();
            } catch (RuntimeException e) {
                // Nada do que acontece dentro de handle() chega aqui — as falhas de
                // processamento são a vida do sistema e tratam-se lá, por mensagem.
                log.error("Ciclo de consumo falhou; continua com a próxima receção", e);
            }
        }
    }

    /**
     * Uma receção da fila e o tratamento de cada mensagem que vier. É o passo que o
     * {@link #poll()} repete enquanto a aplicação viver; está destacado num método
     * package-private para um teste o poder correr uma vez, sem o fio de fundo.
     *
     * @return quantas mensagens foram recebidas nesta ronda
     */
    int drainOnce() {
        List<Message> messages = queues.receiveFromMain();
        messages.forEach(this::handle);
        return messages.size();
    }

    /**
     * Uma mensagem: parse, processamento de cada objeto que anuncia, e a decisão de a
     * apagar. Apagar é a parte que só acontece quando tudo correu bem — o resto das
     * vezes a mensagem volta, e esgota as entregas até à DLQ.
     */
    private void handle(Message message) {
        MDC.put("messageId", message.messageId());
        restoreCorrelation(message);
        try {
            List<S3ObjectReference> references = parser.parse(message.body());
            boolean ack = true;
            for (S3ObjectReference reference : references) {
                if (processor.process(
                                reference.bucket(), reference.key(), receiveCount(message) >= props.maxReceiveCount())
                        == ProcessingOutcome.FAILED) {
                    ack = false;
                }
            }
            if (ack) {
                queues.deleteFromMain(message.receiptHandle());
            } else {
                log.info(
                        "Mensagem {} devolvida à fila; reentrega ou, esgotadas as tentativas, DLQ",
                        message.messageId());
            }
        } catch (DomainException e) {
            // Evento ilegível: permanente, não se processa — apagar seria esconder, a
            // mensagem segue o seu caminho até à DLQ para ser inspecionada.
            log.error("Mensagem {}: {}; segue para a DLQ", message.messageId(), e.getMessage());
        } finally {
            MDC.remove("messageId");
            Correlation.clear();
        }
    }

    /**
     * O id de correlação que veio como atributo de mensagem — só existe no que a
     * aplicação envia (o redrive da DLQ); o caminho normal, via S3, não o tem e o
     * elo passa pela base de dados. Um valor que não passa no saneamento não entra:
     * um id inválido nos logs seria pior do que nenhum.
     */
    private static void restoreCorrelation(Message message) {
        MessageAttributeValue attribute = message.messageAttributes().get(Correlation.SQS_ATTRIBUTE);
        if (attribute != null && Correlation.isValid(attribute.stringValue())) {
            Correlation.set(attribute.stringValue());
        }
    }

    private static int receiveCount(Message message) {
        String count = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        return count == null ? 1 : Integer.parseInt(count);
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE_ON_QUEUE_UNAVAILABLE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
