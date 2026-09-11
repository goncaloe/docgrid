package com.docgrid.document;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.ApprovalThresholdProvider;
import com.docgrid.auth.UserRole;
import com.docgrid.supplier.SupplierApprovalRecorder;

/**
 * Aprova um documento: transita para {@code APPROVED} e, se um humano escolheu uma
 * categoria, alimenta o histórico do fornecedor.
 *
 * <p>Uma despesa acima do limite de aprovação da organização só pode ser aprovada por
 * {@code MANAGER} ou {@code ADMIN} — é a "aprovação de gestor" do produto, decidida no
 * momento de aprovar e não como um estado próprio no ciclo de vida do documento (ver
 * {@code docs/adr/0011}).
 */
@Service
public class DocumentApprovalService {

    private final DocumentRepository documents;
    private final DocumentEventRepository events;
    private final ExtractedFieldRepository fields;
    private final SupplierApprovalRecorder supplierApprovals;
    private final ApprovalThresholdProvider approvalThresholds;

    DocumentApprovalService(
            DocumentRepository documents,
            DocumentEventRepository events,
            ExtractedFieldRepository fields,
            SupplierApprovalRecorder supplierApprovals,
            ApprovalThresholdProvider approvalThresholds) {
        this.documents = documents;
        this.events = events;
        this.fields = fields;
        this.supplierApprovals = supplierApprovals;
        this.approvalThresholds = approvalThresholds;
    }

    /**
     * @param category a categoria com que o revisor aprovou o documento; {@code null} se
     *     não escolheu nenhuma — nesse caso o histórico do fornecedor não muda
     * @throws DocumentNotFoundException se o documento não existir nesta organização
     * @throws AccessDeniedException se o total exceder o limite da organização e quem
     *     aprova não for {@code MANAGER} nem {@code ADMIN}
     */
    @Transactional
    public void approve(UUID documentId, UUID organizationId, Actor actor, UserRole actorRole, String category) {
        Document document = documents
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        requireApprovalAuthority(document, organizationId, actorRole);

        events.save(document.transitionTo(DocumentStatus.APPROVED, actor, null));

        if (category != null && document.getSupplierTaxId() != null) {
            supplierApprovals.recordApproval(
                    document.getOrganizationId(), document.getSupplierTaxId(), supplierName(document), category);
        }
    }

    private void requireApprovalAuthority(Document document, UUID organizationId, UserRole actorRole) {
        if (document.getTotalAmount() == null || actorRole == UserRole.MANAGER || actorRole == UserRole.ADMIN) {
            return;
        }
        if (document.getTotalAmount().compareTo(approvalThresholds.approvalThresholdFor(organizationId)) > 0) {
            throw new AccessDeniedException(
                    "Despesa acima do limite de aprovação da organização: só um gestor pode aprová-la");
        }
    }

    /**
     * O nome do fornecedor não vive em {@code documents} (ADR 0003) — só em
     * {@code extracted_fields}. O próprio NIF serve de rede de segurança defensiva: não
     * deveria faltar, mas uma aprovação não pode rebentar por causa disso.
     */
    private String supplierName(Document document) {
        return fields.findByDocumentIdAndFieldName(document.getId(), ExtractedFieldName.SUPPLIER_NAME)
                .map(ExtractedField::getValueText)
                .orElse(document.getSupplierTaxId());
    }
}
