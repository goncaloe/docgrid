package com.docgrid.document;

import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Rejeita um documento — inválido, duplicado, ou simplesmente não é uma fatura. Delega em
 * {@link DocumentService}: rejeitar não é mais do que a transição para {@code REJECTED},
 * com o motivo escrito por quem rejeita.
 */
@Service
public class DocumentRejectionService {

    private final DocumentService documents;

    DocumentRejectionService(DocumentService documents) {
        this.documents = documents;
    }

    /**
     * @throws DocumentNotFoundException se o documento não existir nesta organização
     * @throws InvalidStatusTransitionException se o documento não estiver em
     *     {@code EXTRACTED} nem {@code NEEDS_REVIEW}
     */
    public void reject(UUID documentId, UUID organizationId, Actor actor, String reason) {
        documents.transition(documentId, organizationId, DocumentStatus.REJECTED, actor, reason);
    }
}
