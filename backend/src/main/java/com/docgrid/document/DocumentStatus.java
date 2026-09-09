package com.docgrid.document;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de um documento e as únicas transições que o sistema aceita.
 *
 * <pre>
 * UPLOADED     → PROCESSING
 * PROCESSING   → EXTRACTED · NEEDS_REVIEW · FAILED
 * EXTRACTED    → APPROVED · NEEDS_REVIEW · REJECTED
 * NEEDS_REVIEW → APPROVED · REJECTED
 * FAILED       → PROCESSING                      (reprocessamento manual)
 * APPROVED     → EXPORTED
 * REJECTED     → (terminal)
 * EXPORTED     → (terminal)
 * </pre>
 *
 * <p>Onze transições em sessenta e quatro pares possíveis. As outras cinquenta e três são
 * recusadas com {@link InvalidStatusTransitionException} — sobretudo as que fariam um
 * documento andar para trás. Um documento aprovado é imutável: se estiver errado, cria-se
 * um documento de correção, não se reabre este.
 *
 * <p>As transições vivem no enum e não numa classe de serviço à parte: são conhecimento do
 * próprio estado e testam-se sem levantar o Spring.
 */
public enum DocumentStatus {

    /** Ficheiro autorizado a subir para o S3, registo criado, ainda por processar. */
    UPLOADED,

    /** Extração em curso, a cargo do worker. */
    PROCESSING,

    /** Extraído e validado sem problemas. A sugestão está pronta para um humano aprovar. */
    EXTRACTED,

    /** Extraído, mas algo não bate certo. O motivo fica em {@code validation_results}. */
    NEEDS_REVIEW,

    /** Erro técnico: ficheiro corrompido, serviço indisponível. Reprocessa-se à mão. */
    FAILED,

    /** Dados confirmados por um humano. Imutável a partir daqui. */
    APPROVED,

    /** Documento inválido, duplicado, ou simplesmente não é uma fatura. Fim. */
    REJECTED,

    /** Incluído numa exportação mensal já fechada. Fim. */
    EXPORTED;

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED;

    static {
        Map<DocumentStatus, Set<DocumentStatus>> allowed = new EnumMap<>(DocumentStatus.class);
        allowed.put(UPLOADED, EnumSet.of(PROCESSING));
        allowed.put(PROCESSING, EnumSet.of(EXTRACTED, NEEDS_REVIEW, FAILED));
        allowed.put(EXTRACTED, EnumSet.of(APPROVED, NEEDS_REVIEW, REJECTED));
        allowed.put(NEEDS_REVIEW, EnumSet.of(APPROVED, REJECTED));
        // Reprocessar é um novo processamento e não um novo upload: o registo já existe
        // e a chave do S3 não muda.
        allowed.put(FAILED, EnumSet.of(PROCESSING));
        allowed.put(APPROVED, EnumSet.of(EXPORTED));
        allowed.put(REJECTED, EnumSet.noneOf(DocumentStatus.class));
        allowed.put(EXPORTED, EnumSet.noneOf(DocumentStatus.class));
        ALLOWED = Collections.unmodifiableMap(allowed);
    }

    public boolean canTransitionTo(DocumentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Os estados alcançáveis a partir deste. Vazio se for terminal. */
    public Set<DocumentStatus> allowedTransitions() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    /** Verdadeiro quando não há saída: o documento acabou aqui. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
