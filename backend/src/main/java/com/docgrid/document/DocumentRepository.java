package com.docgrid.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * As consultas de que as etapas seguintes precisam, todas derivadas do nome do método.
 *
 * <p>Repare-se que quase todas levam {@code organizationId}: o isolamento por organização
 * não é uma verificação que se faz depois de ler, é parte da consulta. Quem o aplica de
 * facto a cada pedido é a etapa 06.
 */
interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Ler um documento pelo id sem o filtro da organização seria uma fuga de dados. */
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** O worker parte da chave do S3 que veio na mensagem (etapa 03). */
    Optional<Document> findByStorageKey(String storageKey);

    /** Idempotência: a mesma mensagem entregue duas vezes não cria trabalho novo (etapa 03). */
    boolean existsByStorageKey(String storageKey);

    /** A fila de revisão e a de aprovação, do mais recente para o mais antigo (etapas 06 e 08). */
    Page<Document> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, DocumentStatus status, Pageable pageable);

    Page<Document> findByOrganizationIdAndStatusInOrderByCreatedAtDesc(
            UUID organizationId, List<DocumentStatus> statuses, Pageable pageable);

    /** Regra do duplicado: a mesma fatura do mesmo fornecedor (etapa 05). */
    List<Document> findByOrganizationIdAndSupplierTaxIdAndInvoiceNumber(
            UUID organizationId, String supplierTaxId, String invoiceNumber);

    /** Duplicado binário: o mesmo ficheiro submetido outra vez (etapa 05). */
    List<Document> findByOrganizationIdAndFileHash(UUID organizationId, String fileHash);

    long countByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status);
}
