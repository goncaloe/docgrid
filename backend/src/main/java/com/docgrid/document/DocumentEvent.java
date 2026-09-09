package com.docgrid.document;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Uma linha do histórico de um documento: o que aconteceu, quando, por ordem de quem e
 * porquê.
 *
 * <p>Não estende {@code BaseEntity}. É um registo de log, append-only: a ordem de
 * inserção tem significado, portanto o identificador é um inteiro sequencial e não um
 * UUID; e não há {@code updated_at} porque um evento não se altera depois de escrito.
 *
 * <p>A tabela não tem {@code on delete cascade} sobre {@code documents}, de propósito: a
 * chave estrangeira torna impossível apagar um documento sem apagar antes o seu histórico.
 */
@Entity
@Table(name = "document_events")
class DocumentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private DocumentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 20)
    private DocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 20)
    private DocumentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    /** Porquê, em português. Opcional para o sistema, esperado de uma pessoa. */
    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    /** Detalhe livre do evento — o valor antigo e o novo, numa correção (etapa 06). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", updatable = false)
    private Map<String, String> payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected DocumentEvent() {}

    private DocumentEvent(
            UUID documentId,
            DocumentEventType eventType,
            DocumentStatus fromStatus,
            DocumentStatus toStatus,
            Actor actor,
            String reason,
            Map<String, String> payload) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actor.type();
        this.actorUserId = actor.userId();
        this.reason = reason;
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    static DocumentEvent statusChanged(
            UUID documentId, DocumentStatus from, DocumentStatus to, Actor actor, String reason) {
        return new DocumentEvent(documentId, DocumentEventType.STATUS_CHANGED, from, to, actor, reason, null);
    }

    /**
     * O valor anterior de um campo corrigido à mão, guardado no payload.
     *
     * <p>É isto que permite a {@code extracted_fields} ter um valor corrente por campo em
     * vez de versões: o histórico da correção vive aqui, na tabela feita para histórico.
     * Quem chama este método é a etapa 06; existe já porque a coluna é desta etapa e uma
     * coluna que não se consegue escrever a partir do Java é meia tabela.
     */
    static DocumentEvent fieldCorrected(
            UUID documentId, ExtractedFieldName fieldName, String oldValue, String newValue, Actor actor) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("field", fieldName.name());
        payload.put("from", oldValue);
        payload.put("to", newValue);
        return new DocumentEvent(documentId, DocumentEventType.FIELD_CORRECTED, null, null, actor, null, payload);
    }

    Long getId() {
        return id;
    }

    UUID getDocumentId() {
        return documentId;
    }

    DocumentEventType getEventType() {
        return eventType;
    }

    DocumentStatus getFromStatus() {
        return fromStatus;
    }

    DocumentStatus getToStatus() {
        return toStatus;
    }

    ActorType getActorType() {
        return actorType;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getReason() {
        return reason;
    }

    Map<String, String> getPayload() {
        return payload == null ? null : Collections.unmodifiableMap(payload);
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}
