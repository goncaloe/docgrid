package com.docgrid.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * Uma fatura ou despesa submetida ao sistema.
 *
 * <p>A organização e o autor da submissão são referidos por id e não por
 * {@code @ManyToOne}: são outros agregados, vivem noutro pacote, e uma referência por id
 * evita carregá-los sempre que se toca num documento. As chaves estrangeiras existem na
 * base de dados, que é onde a integridade se garante.
 *
 * <p>Não há {@code setStatus}. A única forma de mudar de estado é
 * {@link #transitionTo(DocumentStatus, Actor, String)}, que valida a transição e devolve
 * o evento de auditoria correspondente — assim não existe caminho que mude o estado sem
 * deixar rasto.
 *
 * <p>As colunas de negócio (fornecedor, número, montantes) são projeção dos campos
 * extraídos, para procurar e agregar. A verdade, com a confiança de cada campo, está em
 * {@link ExtractedField}. Ver {@code docs/adr/0003-desenho-de-extracted-fields.md}.
 */
@Entity
@Table(name = "documents")
class Document extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Só se sabe depois de o ficheiro subir de facto (etapa 02). */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** SHA-256 em hexadecimal. Serve a deteção de duplicados binários (etapa 05). */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "supplier_tax_id", length = 20)
    private String supplierTaxId;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "net_amount", precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", precision = 12, scale = 2)
    private BigDecimal vatAmount;

    /** Percentagem, não fração: 23.00 e não 0.23. */
    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ExtractedField> extractedFields = new ArrayList<>();

    protected Document() {}

    /**
     * Construtor de chave explícita. Usam-no os testes que precisam de fixar a chave do S3
     * (unicidade, consulta por chave). O caminho de produção é {@link #forUpload}.
     */
    Document(UUID organizationId, UUID submittedBy, String storageKey, String originalFilename, String contentType) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.status = DocumentStatus.UPLOADED;
    }

    /**
     * Um documento novo, no instante em que se emite a autorização de upload. A chave do
     * S3 deriva do próprio id, que já existe: {@link com.docgrid.shared.BaseEntity} gera-o
     * no construtor e não no {@code flush}. Daí ser uma fábrica e não um construtor — a
     * chave não se conhece antes de o objeto existir.
     */
    static Document forUpload(
            UUID organizationId,
            UUID submittedBy,
            String originalFilename,
            String contentType,
            String storageKeyPrefix,
            String extension) {
        Document document = new Document();
        document.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        document.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        document.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        document.contentType = Objects.requireNonNull(contentType, "contentType");
        document.storageKey = "%s/%s.%s".formatted(storageKeyPrefix, document.getId(), extension);
        document.status = DocumentStatus.UPLOADED;
        return document;
    }

    /**
     * Muda o estado do documento, se o ciclo de vida o permitir, e devolve o evento que
     * regista a mudança. Quem chama grava o evento — ver {@link DocumentService}.
     *
     * @throws InvalidStatusTransitionException se a transição não existir no ciclo de vida;
     *     o documento fica intacto
     */
    DocumentEvent transitionTo(DocumentStatus target, Actor actor, String reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        DocumentStatus previous = status;
        this.status = target;
        return DocumentEvent.statusChanged(getId(), previous, target, actor, reason);
    }

    /**
     * Escreve a projeção dos campos de negócio nas colunas de {@code documents}.
     *
     * <p>Este é o único ponto do código que lhes toca. A verdade continua a ser
     * {@link ExtractedField}, com a confiança de cada campo; estas colunas existem para
     * procurar duplicados (etapa 05) e agregar (etapa 09), operações que sobre uma linha
     * por campo sairiam caras e ilegíveis.
     */
    void projectInvoiceFields(InvoiceFields fields) {
        this.supplierTaxId = fields.supplierTaxId();
        this.invoiceNumber = fields.invoiceNumber();
        this.issueDate = fields.issueDate();
        this.netAmount = fields.netAmount();
        this.vatAmount = fields.vatAmount();
        this.vatRate = fields.vatRate();
        this.totalAmount = fields.totalAmount();
    }

    void addExtractedField(ExtractedField field) {
        extractedFields.add(field);
    }

    List<ExtractedField> getExtractedFields() {
        return Collections.unmodifiableList(extractedFields);
    }

    /** Preenchido quando o ficheiro chega ao S3 (etapa 02). */
    void recordUploadedFile(long sizeBytes, String fileHash) {
        this.sizeBytes = sizeBytes;
        this.fileHash = Objects.requireNonNull(fileHash, "fileHash");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getSubmittedBy() {
        return submittedBy;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    String getContentType() {
        return contentType;
    }

    Long getSizeBytes() {
        return sizeBytes;
    }

    String getFileHash() {
        return fileHash;
    }

    DocumentStatus getStatus() {
        return status;
    }

    String getSupplierTaxId() {
        return supplierTaxId;
    }

    String getInvoiceNumber() {
        return invoiceNumber;
    }

    LocalDate getIssueDate() {
        return issueDate;
    }

    BigDecimal getNetAmount() {
        return netAmount;
    }

    BigDecimal getVatAmount() {
        return vatAmount;
    }

    BigDecimal getVatRate() {
        return vatRate;
    }

    BigDecimal getTotalAmount() {
        return totalAmount;
    }

    String getCurrency() {
        return currency;
    }
}
