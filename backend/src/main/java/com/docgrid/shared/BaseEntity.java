package com.docgrid.shared;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.Hibernate;

/**
 * Identidade e marcas temporais comuns às entidades de negócio.
 *
 * <p>O identificador é gerado no construtor e não pelo Hibernate. Existe, portanto, antes
 * de a entidade ser persistida: {@code equals} e {@code hashCode} funcionam em coleções
 * ainda antes do primeiro {@code flush}, e a etapa 02 pode construir a chave do S3 a
 * partir do id sem ter de gravar a linha primeiro.
 *
 * <p>A comparação usa {@link Hibernate#getClass} e não {@code getClass()}, porque um
 * {@code @ManyToOne} preguiçoso chega como proxy e o proxy é de uma subclasse gerada.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseEntity() {}

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id.equals(((BaseEntity) other).id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
