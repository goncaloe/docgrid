package com.docgrid.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.auth.UserRole;
import com.docgrid.document.dto.ApproveRequest;
import com.docgrid.document.dto.DocumentDetailResponse;
import com.docgrid.document.dto.DocumentSummaryResponse;
import com.docgrid.document.dto.FieldCorrectionRequest;
import com.docgrid.document.dto.PageResponse;
import com.docgrid.document.dto.RejectRequest;

/**
 * Listar, ver o detalhe, corrigir, aprovar e rejeitar documentos, e as duas filas de
 * trabalho. Isolamento por organização em todas as consultas, sem exceção — vem sempre de
 * {@link CurrentUserProvider}, nunca do pedido. Um {@code EMPLOYEE} só vê o que submeteu.
 */
@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private final DocumentRepository documents;
    private final DocumentResponseMapper mapper;
    private final CurrentUserProvider currentUser;
    private final DocumentApprovalService approvals;
    private final DocumentRejectionService rejections;
    private final FieldCorrectionService corrections;

    DocumentController(
            DocumentRepository documents,
            DocumentResponseMapper mapper,
            CurrentUserProvider currentUser,
            DocumentApprovalService approvals,
            DocumentRejectionService rejections,
            FieldCorrectionService corrections) {
        this.documents = documents;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.approvals = approvals;
        this.rejections = rejections;
        this.corrections = corrections;
    }

    @GetMapping
    PageResponse<DocumentSummaryResponse> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String supplierTaxId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Document> page = documents.search(
                currentUser.currentOrganizationId(), ownFilterOrNull(), status, supplierTaxId, from, to, pageable);
        return PageResponse.of(page.map(mapper::toSummary));
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
    PageResponse<DocumentSummaryResponse> reviewQueue(@PageableDefault(size = 20) Pageable pageable) {
        Page<Document> page = documents.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                currentUser.currentOrganizationId(), DocumentStatus.NEEDS_REVIEW, pageable);
        return PageResponse.of(page.map(mapper::toSummary));
    }

    @GetMapping("/approval-queue")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
    PageResponse<DocumentSummaryResponse> approvalQueue(@PageableDefault(size = 20) Pageable pageable) {
        Page<Document> page = documents.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                currentUser.currentOrganizationId(), DocumentStatus.EXTRACTED, pageable);
        return PageResponse.of(page.map(mapper::toSummary));
    }

    @GetMapping("/{id}")
    DocumentDetailResponse detail(@PathVariable UUID id) {
        return mapper.toDetail(findOwnedDocument(id));
    }

    @PatchMapping("/{id}/fields/{fieldName}")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
    void correctField(
            @PathVariable UUID id,
            @PathVariable ExtractedFieldName fieldName,
            @Valid @RequestBody FieldCorrectionRequest request) {
        corrections.correct(id, currentUser.currentOrganizationId(), fieldName, request.value(), actingUser());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
    void approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ApproveRequest request) {
        String category = request == null ? null : request.category();
        approvals.approve(id, currentUser.currentOrganizationId(), actingUser(), currentUser.currentRole(), category);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        rejections.reject(id, currentUser.currentOrganizationId(), actingUser(), request.reason());
    }

    /**
     * Um {@code EMPLOYEE} só encontra o que ele próprio submeteu — um documento de outro
     * colega, na mesma organização, é tratado como inexistente, tal como um de outra
     * organização.
     */
    private Document findOwnedDocument(UUID id) {
        Document document = documents
                .findDetailByIdAndOrganizationId(id, currentUser.currentOrganizationId())
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (currentUser.currentRole() == UserRole.EMPLOYEE
                && !document.getSubmittedBy().equals(currentUser.currentUserId())) {
            throw new DocumentNotFoundException(id);
        }
        return document;
    }

    private UUID ownFilterOrNull() {
        return currentUser.currentRole() == UserRole.EMPLOYEE ? currentUser.currentUserId() : null;
    }

    private Actor actingUser() {
        return Actor.user(currentUser.currentUserId());
    }
}
