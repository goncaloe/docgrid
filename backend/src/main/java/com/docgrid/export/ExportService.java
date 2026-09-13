package com.docgrid.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.document.Actor;
import com.docgrid.document.DocumentExportService;
import com.docgrid.export.dto.ExportResponse;
import com.docgrid.storage.StorageService;

/**
 * Fecho de períodos contabilísticos: geração do CSV, arquivo no S3 e marcação dos
 * documentos como exportados.
 *
 * <p>A operação é idempotente: o mesmo período só se exporta uma vez. O índice único em
 * {@code (organization_id, period_year, period_month)} garante-o na base de dados; em
 * caso de condição de corrida o segundo pedido devolve a exportação já existente.
 */
@Service
@Transactional
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ExportRepository repository;
    private final ExportQueries queries;
    private final ExportCsvWriter csvWriter;
    private final StorageService storage;
    private final DocumentExportService documentExportService;
    private final CurrentUserProvider currentUser;

    ExportService(
            ExportRepository repository,
            ExportQueries queries,
            ExportCsvWriter csvWriter,
            StorageService storage,
            DocumentExportService documentExportService,
            CurrentUserProvider currentUser) {
        this.repository = repository;
        this.queries = queries;
        this.csvWriter = csvWriter;
        this.storage = storage;
        this.documentExportService = documentExportService;
        this.currentUser = currentUser;
    }

    /**
     * Cria uma exportação para o período indicado, ou devolve a existente se já tiver
     * sido fechada.
     *
     * @param organizationId organização a que pertence a exportação
     * @param period         mês e ano a exportar
     * @param actor          quem desencadeou a operação
     * @return a resposta da exportação (201 se criada, 200 se já existia)
     * @throws PeriodNotClosableException se o período ainda não terminou
     */
    @Transactional
    public ExportResponse create(UUID organizationId, YearMonth period, Actor actor) {
        YearMonth now = YearMonth.now();
        if (period.isAfter(now)) {
            throw new PeriodNotClosableException(period);
        }

        Export existing = repository
                .findByOrganizationIdAndPeriodYearAndPeriodMonth(
                        organizationId, period.getYear(), period.getMonthValue())
                .orElse(null);
        if (existing != null) {
            return toResponse(existing, queries.countApprovedWithoutIssueDate(organizationId));
        }

        LocalDate endExclusive = period.plusMonths(1).atDay(1);
        List<ExportRow> rows = queries.selectApprovedDocumentsForExport(organizationId, endExclusive);

        UUID exportId = UUID.randomUUID();
        String storageKey = "exports/%s/%s/%s.csv".formatted(organizationId, period, exportId);

        byte[] csvBytes = csvWriter.write(rows);
        storage.put(storageKey, "text/csv; charset=utf-8", csvBytes);

        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (ExportRow row : rows) {
            netTotal = netTotal.add(row.netAmount());
            vatTotal = vatTotal.add(row.vatAmount());
            total = total.add(row.totalAmount());
        }
        int documentCount = rows.size();

        try {
            Export export = Export.withId(
                    exportId,
                    organizationId,
                    period.getYear(),
                    period.getMonthValue(),
                    storageKey,
                    documentCount,
                    netTotal,
                    vatTotal,
                    total,
                    actor.userId() != null ? actor.userId() : currentUser.currentUserId());

            repository.save(export);

            var docIds = rows.stream().map(ExportRow::documentId).toList();
            documentExportService.markExported(organizationId, exportId, docIds, actor);

            long withoutDate = queries.countApprovedWithoutIssueDate(organizationId);
            log.info("Exportação {} do período {} criada com {} documentos", exportId, period, documentCount);
            return toResponse(export, withoutDate);

        } catch (DataIntegrityViolationException e) {
            // Condição de corrida: outra thread criou a exportação entre a verificação
            // e a gravação. Re-ler e devolver a existente.
            Export concurrent = repository
                    .findByOrganizationIdAndPeriodYearAndPeriodMonth(
                            organizationId, period.getYear(), period.getMonthValue())
                    .orElseThrow(() -> new IllegalStateException(
                            "Violação de integridade sem exportação concorrente para %s".formatted(period), e));
            return toResponse(concurrent, queries.countApprovedWithoutIssueDate(organizationId));
        }
    }

    /**
     * Devolve a exportação de um período, se existir.
     */
    @Transactional(readOnly = true)
    public ExportResponse getExisting(UUID organizationId, YearMonth period) {
        Export export = repository
                .findByOrganizationIdAndPeriodYearAndPeriodMonth(
                        organizationId, period.getYear(), period.getMonthValue())
                .orElse(null);
        if (export == null) {
            return null;
        }
        return toResponse(export, queries.countApprovedWithoutIssueDate(organizationId));
    }

    /**
     * Lista exportações de um ano, da mais recente para a mais antiga.
     */
    @Transactional(readOnly = true)
    public List<ExportResponse> listByYear(UUID organizationId, int year) {
        long withoutDate = queries.countApprovedWithoutIssueDate(organizationId);
        return repository.findByOrganizationIdAndPeriodYearOrderByPeriodMonthDesc(organizationId, year).stream()
                .map(e -> toResponse(e, withoutDate))
                .toList();
    }

    /**
     * Devolve uma exportação pelo id, ou {@code null} se não existir ou não pertencer
     * à organização indicada.
     */
    @Transactional(readOnly = true)
    public ExportResponse getExportById(UUID id, UUID organizationId) {
        return repository
                .findByIdAndOrganizationId(id, organizationId)
                .map(e -> toResponse(e, queries.countApprovedWithoutIssueDate(organizationId)))
                .orElse(null);
    }

    private static ExportResponse toResponse(Export export, long documentsWithoutDate) {
        return new ExportResponse(
                export.getId(),
                export.getPeriodYear(),
                export.getPeriodMonth(),
                export.getDocumentCount(),
                export.getNetTotal(),
                export.getVatTotal(),
                export.getTotal(),
                export.getCreatedAt(),
                documentsWithoutDate);
    }
}
