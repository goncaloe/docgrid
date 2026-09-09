package com.docgrid.validation;

/**
 * Quão grave é o que uma regra encontrou.
 *
 * <p>Nenhuma severidade rejeita um documento sozinha. O sistema não substitui o humano:
 * na dúvida, sinaliza para revisão e deixa a decisão a quem a pode tomar.
 */
public enum ValidationSeverity {
    /** Observação útil, sem consequência para o encaminhamento. */
    INFO,
    /** Algo não bate certo. Manda o documento para NEEDS_REVIEW. */
    WARNING,
    /** Impossível continuar sem intervenção humana. Também manda para NEEDS_REVIEW. */
    ERROR
}
