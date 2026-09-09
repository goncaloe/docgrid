package com.docgrid.supplier;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByOrganizationIdAndTaxId(UUID organizationId, String taxId);
}
