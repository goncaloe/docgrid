package com.docgrid.export.dto;

import java.time.Instant;

/**
 * Uma autorização temporária para descarregar o ficheiro CSV de uma exportação.
 *
 * <p>O nome difere de {@code com.docgrid.document.dto.FileUrlResponse} para não haver
 * ambiguidade nos imports de quem usa ambos.
 *
 * @param url       URL assinado de descarga
 * @param expiresAt instante a partir do qual o URL expira
 */
public record ExportFileUrlResponse(String url, Instant expiresAt) {}
