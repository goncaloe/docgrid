package com.docgrid.document;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O único caminho para mudar o estado de um documento.
 *
 * <p>Podia ter-se posto a escrita da auditoria num {@code @EntityListeners} e ela sairia
 * sozinha a cada gravação. Não sairia: um listener não sabe quem provocou a mudança nem
 * porquê, e são essas duas colunas que dão valor a {@code document_events}. Um registo de
 * auditoria que diz "o estado mudou" e não diz por ordem de quem não serve para nada.
 *
 * <p>Por isso a transição e o evento ficam juntos aqui, num método só, dentro da mesma
 * transação: ou acontecem os dois, ou não acontece nenhum.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documents;
    private final DocumentEventRepository events;

    DocumentService(DocumentRepository documents, DocumentEventRepository events) {
        this.documents = documents;
        this.events = events;
    }

    /**
     * Muda o documento para {@code target} e regista a mudança no histórico.
     *
     * @param reason porquê, em português; pode ser nulo quando é o sistema a agir e o
     *     motivo é evidente pelo par de estados
     * @throws DocumentNotFoundException se o documento não existir nesta organização — ler
     *     um documento de outra organização é tratado como se não existisse, e não como
     *     falta de permissão, para a resposta não revelar que existe
     * @throws InvalidStatusTransitionException se o ciclo de vida não permitir a transição;
     *     nesse caso nada é escrito
     */
    @Transactional
    public void transition(UUID documentId, UUID organizationId, DocumentStatus target, Actor actor, String reason) {
        Document document = documents
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        DocumentStatus from = document.getStatus();

        events.save(document.transitionTo(target, actor, reason));

        log.info("Documento {} passou de {} para {} por {}", documentId, from, target, actor.type());
    }
}
