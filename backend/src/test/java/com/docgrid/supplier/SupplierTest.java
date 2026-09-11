package com.docgrid.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A sequência de aprovações consecutivas na mesma categoria, sem tocar na base de dados. */
class SupplierTest {

    private final Supplier supplier = new Supplier(UUID.randomUUID(), "501442889", "Galp Energia");

    @Test
    void startsWithNoCategoryAndNoOccurrences() {
        assertThat(supplier.getUsualCategory()).isNull();
        assertThat(supplier.getOccurrenceCount()).isZero();
    }

    @Test
    void countsConsecutiveApprovalsInTheSameCategory() {
        supplier.recordApproval("Combustível");
        supplier.recordApproval("Combustível");
        supplier.recordApproval("Combustível");

        assertThat(supplier.getUsualCategory()).isEqualTo("Combustível");
        assertThat(supplier.getOccurrenceCount()).isEqualTo(3);
    }

    @Test
    void changingCategoryRestartsTheStreakAtOne() {
        supplier.recordApproval("Combustível");
        supplier.recordApproval("Combustível");

        supplier.recordApproval("Manutenção");

        assertThat(supplier.getUsualCategory()).isEqualTo("Manutenção");
        assertThat(supplier.getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void refusesANullCategory() {
        assertThatThrownBy(() -> supplier.recordApproval(null)).isInstanceOf(NullPointerException.class);
    }
}
