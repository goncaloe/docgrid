package com.docgrid.auth;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O limite de aprovação da organização, para quem precisa dele fora do pacote
 * {@code auth} — hoje, o motor de validação da etapa 05.
 */
public interface ApprovalThresholdProvider {

    BigDecimal approvalThresholdFor(UUID organizationId);
}
