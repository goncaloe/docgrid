package com.docgrid.document.dto;

import java.net.URI;
import java.time.Instant;

/** Uma autorização temporária para ler o ficheiro de um documento. */
public record FileUrlResponse(URI url, Instant expiresAt) {}
