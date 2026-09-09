package com.docgrid.validation;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * O que uma regra de negócio disse sobre um documento.
 *
 * <p>O documento é referido pelo id: é outro agregado, noutro pacote, e a entidade
 * {@code Document} não sai de lá. A chave estrangeira existe na base de dados.
 *
 * <p>Uma linha por regra e por documento. Revalidar reescreve a linha em vez de acumular
 * histórico — o que interessa a quem revê é o que está errado agora, e o histórico de
 * estados já vive em {@code document_events}.
 *
 * <p>O motor que preenche esta tabela é a etapa 05.
 */
@Entity
@Table(name = "validation_results")
class ValidationResult extends BaseEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "rule_name", nullable = false, updatable = false, length = 50)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private ValidationSeverity severity;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    /** Em português: é esta a frase que aparece a quem revê o documento. */
    @Column(name = "message", length = 500)
    private String message;

    protected ValidationResult() {}

    ValidationResult(UUID documentId, String ruleName, ValidationSeverity severity, boolean passed, String message) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.passed = passed;
        this.message = message;
    }

    UUID getDocumentId() {
        return documentId;
    }

    String getRuleName() {
        return ruleName;
    }

    ValidationSeverity getSeverity() {
        return severity;
    }

    boolean hasPassed() {
        return passed;
    }

    String getMessage() {
        return message;
    }
}
