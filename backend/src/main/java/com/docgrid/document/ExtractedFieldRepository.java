package com.docgrid.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {

    List<ExtractedField> findByDocumentId(UUID documentId);

    /** A correção humana da etapa 06 vai buscar um campo em concreto. */
    Optional<ExtractedField> findByDocumentIdAndFieldName(UUID documentId, ExtractedFieldName fieldName);
}
