package com.docgrid.supplier;

import java.util.UUID;

public interface SupplierHistoryProvider {

    SupplierHistory historyFor(UUID organizationId, String taxId);
}
