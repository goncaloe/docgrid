package com.docgrid.supplier;

/**
 * O que se sabe de um fornecedor, para quem precisa disto fora do pacote {@code supplier}
 * — hoje, a sugestão de categoria do motor de validação da etapa 05.
 */
public record SupplierHistory(String usualCategory, int occurrenceCount) {

    /** Nenhum histórico — fornecedor desconhecido, ou ainda sem NIF lido. */
    public static SupplierHistory unknown() {
        return new SupplierHistory(null, 0);
    }
}
