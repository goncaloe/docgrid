package com.docgrid.document;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A marca de que o pipeline já reclamou este objeto do S3.
 *
 * <p>É a tabela da idempotência (etapa 03): a chave primária é a chave do S3, portanto a
 * primeira entrega ganha o {@code insert} e as duplicadas apanham a violação da chave. A
 * coluna {@code completed_at} distingue um duplicado verdadeiro (trabalho concluído, nada
 * a fazer) de uma tentativa interrompida a meio (o documento ficou em {@code PROCESSING},
 * o trabalho retoma). Ver {@code docs/adr/0007-idempotencia-do-worker.md}.
 *
 * <p>Não estende {@code BaseEntity}: a chave é natural e conhecida antes de persistir, e
 * um claim não se cria nem se atualiza fora de dois momentos bem definidos — reclamar e
 * concluir.
 */
@Entity
@Table(name = "processing_claims")
class ProcessingClaim {

    @Id
    @Column(name = "storage_key", nullable = false, updatable = false, length = 500)
    private String storageKey;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProcessingClaim() {}

    /** O primeiro claim de um objeto, no momento em que o documento passa a PROCESSING. */
    ProcessingClaim(UUID documentId, String storageKey) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.claimedAt = Instant.now();
    }

    /** Marca o trabalho como concluído. Chama-o quem passa o documento a EXTRACTED. */
    void complete() {
        this.completedAt = Instant.now();
    }

    /**
     * Reabre o claim para uma nova tentativa. Chama-o quem reprocessa um documento
     * {@code FAILED} (reprocessamento manual a partir da DLQ): a próxima entrega da
     * mensagem vê o claim incompleto e retoma o trabalho, em vez de o tratar como
     * duplicado.
     */
    void reopen() {
        this.completedAt = null;
        this.claimedAt = Instant.now();
    }

    boolean isCompleted() {
        return completedAt != null;
    }

    String getStorageKey() {
        return storageKey;
    }

    UUID getDocumentId() {
        return documentId;
    }

    Instant getClaimedAt() {
        return claimedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
