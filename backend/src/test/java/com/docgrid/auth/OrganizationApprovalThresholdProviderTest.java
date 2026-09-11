package com.docgrid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.docgrid.support.RepositoryTest;

@RepositoryTest
@Import(OrganizationApprovalThresholdProvider.class)
class OrganizationApprovalThresholdProviderTest {

    @Autowired
    private ApprovalThresholdProvider provider;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void readsTheThresholdOfAnExistingOrganization() {
        UUID organizationId = AuthFixtures.organization(entityManager);
        entityManager.flush();

        assertThat(provider.approvalThresholdFor(organizationId)).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void refusesAnOrganizationThatDoesNotExist() {
        assertThatThrownBy(() -> provider.approvalThresholdFor(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
