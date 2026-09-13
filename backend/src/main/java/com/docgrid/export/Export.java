package com.docgrid.export;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.docgrid.shared.BaseEntity;

/**
 * Uma exportação mensal fechada: o CSV com os documentos {@code EXPORTED} do período está
 * no S3 e os valores de negócio (totais, contagem) são a fotografia do momento em que se
 * fechou.
 *
 * <p>É imutável depois de criada — não há {@code set} para os campos de negócio nem
 * transições de estado possíveis. O único construtor recebe tudo o que é preciso e a
 * entidade passa a vida a ser lida.
 */
@Entity
@Table(name = "exports")
class Export extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int periodMonth;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 500)
    private String storageKey;

    @Column(name = "document_count", nullable = false, updatable = false)
    private int documentCount;

    @Column(name = "net_total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal netTotal;

    @Column(name = "vat_total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal vatTotal;

    @Column(name = "total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal total;

    /** Quem desencadeou a exportação. Referência a {@code users(id)}. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdById;

    protected Export() {}

    /**
     * Cria uma exportação com o identificador explícito {@code id}. Usado pelo
     * {@link ExportService} para que o id do CSV, o id da exportação e a chave do S3
     * partilhem o mesmo valor.
     *
     * <p>A identidade está num campo privado de {@link com.docgrid.shared.BaseEntity},
     * por isso usamos reflexão para a fixar.
     */
    static Export withId(
            UUID id,
            UUID organizationId,
            int periodYear,
            int periodMonth,
            String storageKey,
            int documentCount,
            BigDecimal netTotal,
            BigDecimal vatTotal,
            BigDecimal total,
            UUID createdById) {
        Export export = new Export();
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(export, id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set export id", e);
        }
        export.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        export.periodYear = periodYear;
        export.periodMonth = periodMonth;
        export.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        export.documentCount = documentCount;
        export.netTotal = Objects.requireNonNull(netTotal, "netTotal");
        export.vatTotal = Objects.requireNonNull(vatTotal, "vatTotal");
        export.total = Objects.requireNonNull(total, "total");
        export.createdById = Objects.requireNonNull(createdById, "createdById");
        return export;
    }

    Export(
            UUID organizationId,
            int periodYear,
            int periodMonth,
            String storageKey,
            int documentCount,
            BigDecimal netTotal,
            BigDecimal vatTotal,
            BigDecimal total,
            UUID createdById) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.documentCount = documentCount;
        this.netTotal = Objects.requireNonNull(netTotal, "netTotal");
        this.vatTotal = Objects.requireNonNull(vatTotal, "vatTotal");
        this.total = Objects.requireNonNull(total, "total");
        this.createdById = Objects.requireNonNull(createdById, "createdById");
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public BigDecimal getNetTotal() {
        return netTotal;
    }

    public BigDecimal getVatTotal() {
        return vatTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public UUID getCreatedById() {
        return createdById;
    }
}
