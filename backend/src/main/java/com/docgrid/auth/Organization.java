package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * A empresa que usa o sistema. Uma organização por instalação, mas o modelo isola-as
 * desde já: tudo o que se segue guarda o seu {@code organization_id}.
 */
@Entity
@Table(name = "organizations")
class Organization extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** NIF da própria empresa. Pode não ser conhecido no registo. */
    @Column(name = "tax_id", length = 20)
    private String taxId;

    /** Total acima do qual a despesa exige aprovação de um gestor. Em EUR. */
    @Column(name = "approval_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal approvalThreshold;

    protected Organization() {}

    Organization(String name, String taxId, BigDecimal approvalThreshold) {
        this.name = Objects.requireNonNull(name, "name");
        this.taxId = taxId;
        this.approvalThreshold = Objects.requireNonNull(approvalThreshold, "approvalThreshold");
    }

    String getName() {
        return name;
    }

    String getTaxId() {
        return taxId;
    }

    BigDecimal getApprovalThreshold() {
        return approvalThreshold;
    }
}
