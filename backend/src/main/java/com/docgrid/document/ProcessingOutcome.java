package com.docgrid.document;

/**
 * O resultado de processar uma entrega, e a única coisa que o consumidor da fila precisa
 * de saber dela: apagar a mensagem ou devolvê-la.
 *
 * <p>Os erros não têm tipo próprio aqui de propósito: {@link #FAILED} cobre tanto o
 * transitório (voltará a chegar e o trabalho retoma) como o permanente (o documento já
 * foi marcado {@code FAILED} e a mensagem segue o seu caminho até à dead-letter queue,
 * onde é inspecionável). Quem decide essa distinção é o {@code DocumentProcessor}; quem
 * consome a fila não volta a pensar nisso.
 */
public enum ProcessingOutcome {

    /** O trabalho foi feito nesta entrega e a mensagem pode ser apagada. */
    PROCESSED,

    /** A mensagem é duplicada de um trabalho já concluído: nada a fazer, apagar. */
    DUPLICATE,

    /** O trabalho não se concluiu. Não apagar: reentrega, e depois a dead-letter queue. */
    FAILED
}
