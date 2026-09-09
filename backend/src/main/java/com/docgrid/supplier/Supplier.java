package com.docgrid.supplier;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * O que a organização já aprendeu sobre um fornecedor.
 *
 * <p>Serve a sugestão de categoria da etapa 05: quando o mesmo NIF aparece repetidamente
 * na mesma categoria, o sistema propõe-na com o histórico como justificação. Propõe — não
 * impõe. O sistema não substitui o humano.
 */
@Entity
@Table(name = "suppliers")
class Supplier extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /** NIF. A validação do dígito de controlo é da etapa 05. */
    @Column(name = "tax_id", nullable = false, updatable = false, length = 20)
    private String taxId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "usual_category", length = 50)
    private String usualCategory;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    protected Supplier() {}

    Supplier(UUID organizationId, String taxId, String name) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.taxId = Objects.requireNonNull(taxId, "taxId");
        this.name = Objects.requireNonNull(name, "name");
        this.occurrenceCount = 0;
    }

    /** Chamado a cada documento aprovado deste fornecedor (etapa 05). */
    void recordOccurrence() {
        occurrenceCount++;
    }

    /**
     * Fixa a categoria a sugerir. Decidir quando o histórico já a sustenta é da etapa 05;
     * aqui só se guarda a conclusão.
     */
    void rememberUsualCategory(String category) {
        this.usualCategory = category;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getTaxId() {
        return taxId;
    }

    String getName() {
        return name;
    }

    String getUsualCategory() {
        return usualCategory;
    }

    int getOccurrenceCount() {
        return occurrenceCount;
    }
}
