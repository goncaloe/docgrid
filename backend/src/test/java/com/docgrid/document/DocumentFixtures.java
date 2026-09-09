package com.docgrid.document;

import java.util.UUID;

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Documentos para os testes de outros pacotes, que só precisam de um id válido para
 * satisfazer a chave estrangeira.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {}

    public static UUID document(TestEntityManager entityManager, UUID organizationId, UUID submittedBy) {
        Document document = new Document(
                organizationId,
                submittedBy,
                "org/%s/2026/09/%s.pdf".formatted(organizationId, UUID.randomUUID()),
                "fatura.pdf",
                "application/pdf");
        entityManager.persist(document);
        return document.getId();
    }
}
