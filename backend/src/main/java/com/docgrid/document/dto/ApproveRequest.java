package com.docgrid.document.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** @param category escolhida pelo revisor; {@code null} se não escolheu nenhuma */
public record ApproveRequest(
        @Size(max = 100) @Schema(example = "Alimentação") String category) {}
