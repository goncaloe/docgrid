package com.docgrid.pipeline.dto;

/**
 * O resultado de reprocessar a dead-letter queue: quantas mensagens voltaram à fila
 * principal para uma nova tentativa.
 */
public record RedriveSummary(int movedToMainQueue) {}
