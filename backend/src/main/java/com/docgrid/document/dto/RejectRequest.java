package com.docgrid.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record RejectRequest(
        @NotBlank @Size(max = 500) @Schema(example = "Não é uma fatura, é um postal publicitário")
        String reason) {}
