package com.docgrid.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * O mesmo documento, com os campos extraídos já carregados.
     *
     * <p>O detalhe é servido fora de transação — {@code DocumentController} não abre
     * nenhuma — e {@code DocumentResponseMapper.toDetail} percorre
     * {@link Document#getExtractedFields()}, que é {@code LAZY}. Sem este {@code EntityGraph}
     * a sessão já fechou quando o mapper lá chega e o pedido termina em
     * {@code LazyInitializationException}. Método à parte de propósito: quem aprova ou
     * rejeita não precisa dos campos e continua a usar a consulta simples acima.
     */
    @EntityGraph(attributePaths = "extractedFields")
    Optional<Document> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

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

    /**
     * A listagem geral (etapa 06): estado, fornecedor e período são filtros opcionais,
     * combináveis. {@code submittedBy} é preenchido só para {@code EMPLOYEE} — vê apenas o
     * que submeteu; os outros papéis veem toda a organização.
     *
     * <p>{@code from}/{@code to} usam {@code coalesce}, não {@code :param is null or ...},
     * porque o Postgres não consegue inferir o tipo de um parâmetro {@code Instant} só
     * comparado em {@code is null} (tenta {@code bytea} e a comparação com {@code timestamptz}
     * rebenta). {@code coalesce(:from, d.createdAt)} usa o parâmetro sempre no mesmo
     * contexto tipado, e vira um no-op quando é nulo.
     */
    @Query("""
            select d from Document d
            where d.organizationId = :organizationId
              and (:submittedBy is null or d.submittedBy = :submittedBy)
              and (:status is null or d.status = :status)
              and (:supplierTaxId is null or d.supplierTaxId = :supplierTaxId)
              and d.createdAt >= coalesce(:from, d.createdAt)
              and d.createdAt <= coalesce(:to, d.createdAt)
            order by d.createdAt desc
            """)
    Page<Document> search(
            @Param("organizationId") UUID organizationId,
            @Param("submittedBy") UUID submittedBy,
            @Param("status") DocumentStatus status,
            @Param("supplierTaxId") String supplierTaxId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
