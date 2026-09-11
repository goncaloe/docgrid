package com.docgrid.validation;

/** O que uma regra disse sobre um documento, para quem precisa disto fora do pacote {@code validation}. */
public record ValidationResultView(String ruleName, ValidationSeverity severity, boolean passed, String message) {}
