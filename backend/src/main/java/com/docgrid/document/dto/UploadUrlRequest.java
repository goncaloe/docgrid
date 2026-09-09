package com.docgrid.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * O que o cliente envia para pedir uma autorização de upload: o nome do ficheiro, o seu
 * {@code Content-Type} e o tamanho que declara ter. O tamanho serve para recusar já um
 * ficheiro grande demais, antes de emitir a autorização.
 */
public record UploadUrlRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes) {}
