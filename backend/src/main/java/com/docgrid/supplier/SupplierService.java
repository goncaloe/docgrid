package com.docgrid.supplier;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SupplierService implements SupplierHistoryProvider, SupplierApprovalRecorder {

    private final SupplierRepository suppliers;

    SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    public SupplierHistory historyFor(UUID organizationId, String taxId) {
        if (taxId == null) {
            return SupplierHistory.unknown();
        }
        return suppliers
                .findByOrganizationIdAndTaxId(organizationId, taxId)
                .map(supplier -> new SupplierHistory(supplier.getUsualCategory(), supplier.getOccurrenceCount()))
                .orElseGet(SupplierHistory::unknown);
    }

    @Override
    @Transactional
    public void recordApproval(UUID organizationId, String taxId, String supplierName, String category) {
        if (category == null) {
            return;
        }
        Supplier supplier = suppliers
                .findByOrganizationIdAndTaxId(organizationId, taxId)
                .orElseGet(() -> suppliers.save(new Supplier(organizationId, taxId, supplierName)));
        supplier.recordApproval(category);
    }
}
