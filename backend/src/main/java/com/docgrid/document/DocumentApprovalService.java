package com.docgrid.document;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * {@code docs/adr/0011}). O mesmo exige-se se algum campo de montante foi corrigido à
 * mão: sem isso, um {@code FINANCE} sem autoridade para aprovar acima do limite corrigia
 * o total para baixo dele e aprovava a seguir, sem gestor nenhum a validar a correção.
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

        // Gravar a categoria na projeção e nos campos extraídos, com evento de correção
        // se o valor mudou (inclusive de nulo para um valor: antes não estava decidida).
        if (category != null) {
            ExtractedField existing = fields.findByDocumentIdAndFieldName(documentId, ExtractedFieldName.CATEGORY)
                    .orElse(null);
            String oldCategory = existing != null ? existing.getValueText() : null;

            document.categoriseAs(category);

            if (existing == null) {
                ExtractedField.writtenByHuman(document, ExtractedFieldName.CATEGORY, category);
            } else if (!category.equals(oldCategory)) {
                existing.correctTo(category);
            }

            if (!Objects.equals(oldCategory, category)) {
                events.save(DocumentEvent.fieldCorrected(
                        documentId, ExtractedFieldName.CATEGORY, oldCategory, category, actor));
            }
        }
    }

    private static final Set<ExtractedFieldName> AMOUNT_FIELDS = EnumSet.of(
            ExtractedFieldName.NET_AMOUNT,
            ExtractedFieldName.VAT_AMOUNT,
            ExtractedFieldName.VAT_RATE,
            ExtractedFieldName.TOTAL_AMOUNT);

    private void requireApprovalAuthority(Document document, UUID organizationId, UserRole actorRole) {
        if (actorRole == UserRole.MANAGER || actorRole == UserRole.ADMIN) {
            return;
        }
        if (hasHumanCorrectedAmount(document)) {
            throw new AccessDeniedException(
                    "Um montante deste documento foi corrigido à mão: só um gestor pode aprová-lo");
        }
        if (document.getTotalAmount() != null
                && document.getTotalAmount().compareTo(approvalThresholds.approvalThresholdFor(organizationId)) > 0) {
            throw new AccessDeniedException(
                    "Despesa acima do limite de aprovação da organização: só um gestor pode aprová-la");
        }
    }

    /**
     * Sem isto, corrigir {@code TOTAL_AMOUNT} para um valor abaixo do limite e aprovar a
     * seguir contornava a verificação acima — o total corrente já refletia a correção.
     */
    private boolean hasHumanCorrectedAmount(Document document) {
        List<ExtractedField> extractedFields = fields.findByDocumentId(document.getId());
        return extractedFields.stream()
                .anyMatch(field ->
                        AMOUNT_FIELDS.contains(field.getFieldName()) && field.getSource() == FieldSource.HUMAN);
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
