package com.docgrid.shared;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * O id de correlação de um pedido: atravessa API → fila → worker e liga os logs que
 * pertencem ao mesmo evento. Vive no MDC, onde o padrão de log e o formatador
 * estruturado o leem; a API devolve-o no header da resposta, o documento guarda-o na
 * coluna {@code correlation_id}, e o que a aplicação envia para a fila (redrive da DLQ)
 * carrega-o como atributo de mensagem.
 *
 * <p>A propagação tem dois caminhos de propósito — ver
 * {@code docs/adr/0015-id-de-correlacao.md}: quem produz a mensagem que o worker consome
 * é o S3, e um evento {@code ObjectCreated} não carrega atributos nossos. O elo entre a
 * API e o worker no caminho normal é a base de dados; o atributo só existe no que nós
 * próprios enviamos.
 */
public final class Correlation {

    /** A chave no MDC; é também o campo de topo {@code correlationId} nos logs ECS. */
    public static final String MDC_KEY = "correlationId";

    /** O header HTTP. Aceita-se do cliente, saneado; devolve-se sempre na resposta. */
    public static final String HEADER = "X-Correlation-Id";

    /** O atributo de mensagem SQS que transporta o id no que a aplicação envia. */
    public static final String SQS_ATTRIBUTE = "X-Correlation-Id";

    /** Teto do tamanho: basta a um UUID canónico; mais que isto é texto a entrar nos logs. */
    static final int MAX_LENGTH = 64;

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1," + MAX_LENGTH + "}$");

    private Correlation() {}

    /**
     * Devolve o candidato se for um id de correlação seguro, senão um UUID novo.
     * Saneamento e não recusa: um header com lixo não deve derrubar o pedido.
     */
    public static String sanitizeOrGenerate(String candidate) {
        return isValid(candidate) ? candidate : UUID.randomUUID().toString();
    }

    /** Verdadeiro se o valor for um id de correlação aceitável (ver {@link #PATTERN}). */
    public static boolean isValid(String candidate) {
        return candidate != null && PATTERN.matcher(candidate).matches();
    }

    /** O id do fio atual, ou {@code null} se não houver nenhum. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Põe o id no MDC do fio atual. */
    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    /** Tira o id do MDC do fio atual. */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
