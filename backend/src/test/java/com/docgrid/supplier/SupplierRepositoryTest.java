package com.docgrid.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.support.RepositoryTest;

/** O histórico por fornecedor que sustenta a sugestão de categoria da etapa 05. */
@RepositoryTest
class SupplierRepositoryTest {

    @Autowired
    private SupplierRepository suppliers;

    @Autowired
    private TestEntityManager entityManager;

    private UUID organizationId;

    @BeforeEach
    void setUp() {
        organizationId = AuthFixtures.organization(entityManager);
        entityManager.flush();
    }

    @Test
    void findsASupplierByTheTaxIdReadFromTheInvoice() {
        suppliers.save(new Supplier(organizationId, "501442889", "Galp Energia"));
        entityManager.flush();
        entityManager.clear();

        assertThat(suppliers.findByOrganizationIdAndTaxId(organizationId, "501442889"))
                .get()
                .satisfies(supplier -> {
                    assertThat(supplier.getName()).isEqualTo("Galp Energia");
                    assertThat(supplier.getOccurrenceCount()).isZero();
                    assertThat(supplier.getUsualCategory()).isNull();
                });
    }

    @Test
    void countsTheOccurrencesThatWillJustifyACategorySuggestion() {
        Supplier supplier = suppliers.save(new Supplier(organizationId, "501442889", "Galp Energia"));
        entityManager.flush();

        for (int i = 0; i < 5; i++) {
            supplier.recordApproval("Combustível");
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(suppliers.findByOrganizationIdAndTaxId(organizationId, "501442889"))
                .get()
                .satisfies(stored -> {
                    assertThat(stored.getOccurrenceCount()).isEqualTo(5);
                    assertThat(stored.getUsualCategory()).isEqualTo("Combustível");
                });
    }

    @Test
    void refusesTheSameTaxIdTwiceInTheSameOrganization() {
        suppliers.save(new Supplier(organizationId, "501442889", "Galp Energia"));
        entityManager.flush();

        suppliers.save(new Supplier(organizationId, "501442889", "Galp Energia, S.A."));

        assertThatThrownBy(suppliers::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void letsTwoOrganizationsKeepTheirOwnHistoryOfTheSameSupplier() {
        UUID otherOrganization = AuthFixtures.organization(entityManager);
        suppliers.save(new Supplier(organizationId, "501442889", "Galp Energia"));
        suppliers.save(new Supplier(otherOrganization, "501442889", "Galp Energia"));
        entityManager.flush();
        entityManager.clear();

        assertThat(suppliers.findByOrganizationIdAndTaxId(organizationId, "501442889"))
                .isPresent();
        assertThat(suppliers.findByOrganizationIdAndTaxId(otherOrganization, "501442889"))
                .isPresent();
    }
}
