package com.docgrid.document;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.supplier.SupplierApprovalRecorder;

/**
 * Aprova um documento: transita para {@code APPROVED} e, se um humano escolheu uma
 * categoria, alimenta o histórico do fornecedor.
 *
 * <p>Sem endpoint REST nesta etapa — a etapa 06 traz a API e a autenticação e liga isto a
 * um controller. Este é o núcleo testável, chamado diretamente pelos testes.
 */
@Service
public class DocumentApprovalService {

    private final DocumentRepository documents;
    private final DocumentEventRepository events;
    private final ExtractedFieldRepository fields;
    private final SupplierApprovalRecorder supplierApprovals;

    DocumentApprovalService(
            DocumentRepository documents,
            DocumentEventRepository events,
            ExtractedFieldRepository fields,
            SupplierApprovalRecorder supplierApprovals) {
        this.documents = documents;
        this.events = events;
        this.fields = fields;
        this.supplierApprovals = supplierApprovals;
    }

    /**
     * @param category a categoria com que o revisor aprovou o documento; {@code null} se
     *     não escolheu nenhuma — nesse caso o histórico do fornecedor não muda
     */
    @Transactional
    public void approve(UUID documentId, Actor actor, String category) {
        Document document = documents.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));

        events.save(document.transitionTo(DocumentStatus.APPROVED, actor, null));

        if (category != null && document.getSupplierTaxId() != null) {
            supplierApprovals.recordApproval(
                    document.getOrganizationId(), document.getSupplierTaxId(), supplierName(document), category);
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
