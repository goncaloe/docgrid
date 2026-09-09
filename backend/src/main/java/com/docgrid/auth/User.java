package com.docgrid.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * Quem usa o sistema.
 *
 * <p>A organização é referida pelo id e não por {@code @ManyToOne}: são agregados
 * distintos, e uma referência por id evita carregar a organização sempre que se toca
 * num utilizador.
 *
 * <p>{@code passwordHash} e {@code role} existem desde a etapa 01 porque a tabela é
 * desta etapa; quem lhes toca é a etapa 06.
 */
@Entity
@Table(name = "users")
class User extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /** Preenchido quando a pessoa deixa de ter acesso. O registo nunca se apaga. */
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    protected User() {}

    User(UUID organizationId, String email, String passwordHash, String fullName, UserRole role) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role");
    }

    void deactivate() {
        if (deactivatedAt == null) {
            deactivatedAt = Instant.now();
        }
    }

    boolean isActive() {
        return deactivatedAt == null;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    UserRole getRole() {
        return role;
    }

    Instant getDeactivatedAt() {
        return deactivatedAt;
    }
}
