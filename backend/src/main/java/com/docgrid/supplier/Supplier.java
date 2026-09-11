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

    /**
     * Chamado a cada documento aprovado deste fornecedor, com a categoria escolhida.
     *
     * <p>{@code occurrenceCount} é o comprimento da sequência de aprovações
     * <strong>consecutivas</strong> na mesma categoria: mantém-se incrementa, muda
     * reinicia a 1. A sugestão só se torna visível a partir de 5 (ver
     * {@code CategorySuggestionRule}, etapa 05) — antes disso, {@code usualCategory} já
     * guarda a categoria da sequência em curso, só ainda não é sugerida.
     */
    void recordApproval(String category) {
        Objects.requireNonNull(category, "category");
        if (category.equals(usualCategory)) {
            occurrenceCount++;
        } else {
            usualCategory = category;
            occurrenceCount = 1;
        }
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
