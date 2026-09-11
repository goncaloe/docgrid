package com.docgrid.supplier;

import java.util.UUID;

/**
 * Regista uma aprovação no histórico do fornecedor. Chamado a cada documento aprovado
 * (etapa 05) — nunca imposto, só alimenta a sugestão de categoria de
 * {@link SupplierHistoryProvider}.
 */
public interface SupplierApprovalRecorder {

    /**
     * @param category a categoria com que o documento foi aprovado; {@code null} se o
     *     revisor não escolheu nenhuma — nesse caso, nada muda no histórico
     */
    void recordApproval(UUID organizationId, String taxId, String supplierName, String category);
}
