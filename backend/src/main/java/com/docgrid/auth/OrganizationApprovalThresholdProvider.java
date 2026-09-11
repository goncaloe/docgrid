package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
class OrganizationApprovalThresholdProvider implements ApprovalThresholdProvider {

    private final OrganizationRepository organizations;

    OrganizationApprovalThresholdProvider(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    public BigDecimal approvalThresholdFor(UUID organizationId) {
        return organizations
                .findById(organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Organização %s referida por um documento não existe".formatted(organizationId)))
                .getApprovalThreshold();
    }
}
