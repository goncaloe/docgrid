package com.docgrid.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Regista a organização e o seu primeiro utilizador, com papel {@code ADMIN}. */
public record RegisterRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Padaria do Bairro, Lda.")
        String organizationName,

        @Size(max = 20) @Schema(example = "501442889") String organizationTaxId,

        @NotBlank @Email @Size(max = 320) @Schema(example = "ana.ribeiro@padaria.pt")
        String adminEmail,

        @NotBlank @Size(min = 8, max = 100) @Schema(example = "uma-password-forte")
        String adminPassword,

        @NotBlank @Size(max = 200) @Schema(example = "Ana Ribeiro")
        String adminFullName) {}
