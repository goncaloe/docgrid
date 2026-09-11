package com.docgrid.validation;

/**
 * O que o {@link ValidationEngine} devolve a quem o chamou — a única coisa que atravessa
 * a fronteira do pacote {@code validation}. Os resultados de cada regra, com a sua
 * severidade e o seu id, ficam em {@code validation_results}; quem processa o documento só
 * precisa de saber se manda para revisão e porquê.
 *
 * @param reason as mensagens das regras que falharam com severidade {@code WARNING} ou
 *     {@code ERROR}, concatenadas com {@code "; "}; {@code null} se nada falhou
 */
public record ValidationSummary(boolean requiresReview, String reason) {}
