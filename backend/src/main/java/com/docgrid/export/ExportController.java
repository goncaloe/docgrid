package com.docgrid.export;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.document.Actor;
import com.docgrid.export.dto.CreateExportRequest;
import com.docgrid.export.dto.ExportFileUrlResponse;
import com.docgrid.export.dto.ExportResponse;
import com.docgrid.storage.StorageService;

/**
 * Fecho de períodos contabilísticos: criar exportações mensais, listar as já fechadas e
 * obter o URL de descarga do CSV.
 *
 * <p>Acesso reservado aos papéis {@code FINANCE}, {@code MANAGER} e {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/exports")
@PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
class ExportController {

    private final ExportService exportService;
    private final CurrentUserProvider currentUser;
    private final StorageService storage;

    ExportController(ExportService exportService, CurrentUserProvider currentUser, StorageService storage) {
        this.exportService = exportService;
        this.currentUser = currentUser;
        this.storage = storage;
    }

    /**
     * Cria uma exportação para o mês e ano indicados, ou devolve a existente se o
     * período já tiver sido fechado.
     *
     * @param request ano e mês do período
     * @return a exportação criada (201) ou a já existente (200 — a resposta do servidor
     *         é a mesma, mas quem inspecionar o {@code Location} ou o {@code status} saberá
     *         se foi criada ou reutilizada)
     * @throws PeriodNotClosableException se o período ainda não terminou
     */
    @PostMapping
    ResponseEntity<ExportResponse> create(@Valid @RequestBody CreateExportRequest request) {
        UUID orgId = currentUser.currentOrganizationId();
        YearMonth period = YearMonth.of(request.year(), request.month());
        Actor actor = Actor.user(currentUser.currentUserId());

        ExportResponse existing = exportService.getExisting(orgId, period);
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(exportService.create(orgId, period, actor));
    }

    /**
     * Lista as exportações de um ano, da mais recente para a mais antiga.
     *
     * @param year ano a consultar (ex. {@code 2026})
     * @return lista de exportações do ano
     */
    @GetMapping
    List<ExportResponse> listByYear(@RequestParam int year) {
        return exportService.listByYear(currentUser.currentOrganizationId(), year);
    }

    /**
     * Devolve um URL temporário para descarregar o ficheiro CSV de uma exportação.
     *
     * @param id identificador da exportação
     * @return URL assinado de descarga
     * @throws ExportNotFoundException se a exportação não existir ou não pertencer
     *         à organização do utilizador
     */
    @GetMapping("/{id}/file-url")
    ExportFileUrlResponse fileUrl(@PathVariable UUID id) {
        UUID orgId = currentUser.currentOrganizationId();

        ExportResponse export = exportService.getExportById(id, orgId);
        if (export == null) {
            throw new ExportNotFoundException(id);
        }

        String storageKey = "exports/%s/%04d-%02d/%s.csv".formatted(orgId, export.year(), export.month(), export.id());

        var presigned = storage.createDownloadUrl(
                storageKey, "exportacao-%04d-%02d.csv".formatted(export.year(), export.month()), Duration.ofHours(1));

        return new ExportFileUrlResponse(presigned.url().toASCIIString(), presigned.expiresAt());
    }
}
