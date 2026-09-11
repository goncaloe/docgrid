package com.docgrid.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldCorrectionRequest(
        @NotBlank @Size(max = 500) @Schema(example = "505123452")
        String value) {}
