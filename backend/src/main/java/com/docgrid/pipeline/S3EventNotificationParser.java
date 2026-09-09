package com.docgrid.pipeline;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Lê o corpo de uma mensagem SQS e extrai os objetos que o evento anuncia.
 *
 * <p>O SDK da AWS para Java v2 não traz um leitor para as notificações de eventos do S3
 * (o v1 tinha, o v2 ainda não), e a dependência que o faria arrasta o módulo inteiro de
 * eventbridge por causa de duas chaves num JSON estável desde 2014 — lê-se à mão.
 *
 * <p>Dois detalhes que este parser trata e que um {@code readTree} descuidado deixaria
 * passar: o {@code s3:TestEvent} que o S3 envia quando a notificação é configurada (não
 * é um evento de objeto — ignora-se), e a chave do objeto, que o S3 envia url-encoded.
 */
@Component
public class S3EventNotificationParser {

    private final ObjectMapper json;

    public S3EventNotificationParser(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @throws MalformedS3EventException se o corpo não for um evento de notificação —
     *     permanente: nunca vai passar a ser legível
     */
    public List<S3ObjectReference> parse(String body) {
        JsonNode root = readTree(body);

        // O ping que o S3 manda ao configurar a notificação: "Event": "s3:TestEvent",
        // sem Records. Não é objeto nenhum, e o worker apaga-o sem pensar mais.
        if ("s3:TestEvent".equals(root.path("Event").asText())) {
            return List.of();
        }

        JsonNode records = root.get("Records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            throw new MalformedS3EventException(body);
        }

        List<S3ObjectReference> references = new ArrayList<>();
        for (JsonNode record : records) {
            // Só os ObjectCreated nos interessam: o bucket não notifica mais nada, mas
            // um dia que mude de ideias não pode produzir trabalho silencioso.
            if (!record.path("eventName").asText().startsWith("ObjectCreated")) {
                continue;
            }
            String bucket = text(body, record, "s3", "bucket", "name");
            String key = decode(text(body, record, "s3", "object", "key"));
            references.add(new S3ObjectReference(bucket, key));
        }
        return List.copyOf(references);
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            throw new MalformedS3EventException(body);
        }
    }

    private static String text(String body, JsonNode record, String... path) {
        JsonNode node = record;
        for (String segment : path) {
            node = node.path(segment);
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            throw new MalformedS3EventException(body);
        }
        return text;
    }

    /** A chave chega url-encoded — sem isto, um espaço vira "+". */
    private static String decode(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }
}
