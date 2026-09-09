package com.docgrid.validation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByDocumentId(UUID documentId);

    /** Revalidar substitui os resultados anteriores em vez de os acumular (etapa 05). */
    void deleteByDocumentId(UUID documentId);
}
