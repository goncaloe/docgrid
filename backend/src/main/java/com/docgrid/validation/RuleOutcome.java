package com.docgrid.validation;

/**
 * O que uma regra concluiu sobre um documento, antes de se tornar uma linha em
 * {@code validation_results}.
 *
 * @param message em português: é esta a frase que aparece a quem revê o documento
 */
public record RuleOutcome(ValidationSeverity severity, boolean passed, String message) {}
