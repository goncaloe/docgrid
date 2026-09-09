package com.docgrid.document;

/**
 * O que aconteceu ao documento.
 *
 * <p>{@link #STATUS_CHANGED} é o que esta etapa escreve. Os outros dois fazem parte do
 * mesmo vocabulário de auditoria e estão desde já na constraint da tabela, para as etapas
 * 02 e 06 não precisarem de uma migração que só alarga um CHECK.
 */
public enum DocumentEventType {

    /** O documento foi registado e a autorização de upload emitida (etapa 02). */
    CREATED,

    /** O documento mudou de estado. Guarda de onde para onde, por quem e porquê. */
    STATUS_CHANGED,

    /** Uma pessoa corrigiu um campo extraído; o valor anterior fica no payload (etapa 06). */
    FIELD_CORRECTED
}
