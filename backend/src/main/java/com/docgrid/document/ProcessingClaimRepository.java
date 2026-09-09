package com.docgrid.document;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Os claims de processamento — a tabela da idempotência do worker (etapa 03). A chave é
 * a chave do S3, portanto basta {@code saveAndFlush} e o {@code findById} herdados: quem
 * perde a corrida de inserção apanha a violação da chave primária.
 */
interface ProcessingClaimRepository extends JpaRepository<ProcessingClaim, String> {}
