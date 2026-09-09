package com.docgrid.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentEventRepository extends JpaRepository<DocumentEvent, Long> {

    /**
     * O histórico de um documento, por ordem. O desempate pelo id não é zelo excessivo:
     * duas transições no mesmo milissegundo têm o mesmo {@code occurred_at}, e sem ele a
     * ordem de leitura ficaria ao critério do Postgres.
     */
    List<DocumentEvent> findByDocumentIdOrderByOccurredAtAscIdAsc(UUID documentId);
}
