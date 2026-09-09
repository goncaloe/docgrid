/**
 * Armazenamento dos ficheiros dos documentos, atrás de uma abstração.
 *
 * <p>O ficheiro nunca passa pelo servidor: o backend emite uma autorização temporária
 * (URL pré-assinado) e o browser fala diretamente com o S3. Ver
 * {@code docs/adr/0005-upload-com-url-pre-assinado.md}.
 */
package com.docgrid.storage;
