/**
 * O pipeline assíncrono: a fila de processamento, o worker que a consome e a
 * administração da dead-letter queue. O que acontece ao documento em si — estados,
 * idempotência, extração — vive em {@code com.docgrid.document}; este pacote é o
 * transporte: entrega as mensagens a quem sabe o que fazer com elas.
 */
package com.docgrid.pipeline;
