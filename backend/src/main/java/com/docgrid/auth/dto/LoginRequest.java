package com.docgrid.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @NotBlank @Email @Schema(example = "ana.ribeiro@padaria.pt")
        String email,

        @NotBlank @Schema(example = "uma-password-forte") String password) {}
