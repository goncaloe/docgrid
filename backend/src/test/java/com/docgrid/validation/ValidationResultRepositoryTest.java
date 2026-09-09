package com.docgrid.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.document.DocumentFixtures;
import com.docgrid.support.RepositoryTest;

/**
 * A tabela onde o motor de regras da etapa 05 vai escrever. Aqui prova-se apenas que ela
 * guarda o que promete: a regra, a gravidade e a frase que aparece a quem revê.
 */
@RepositoryTest
class ValidationResultRepositoryTest {

    @Autowired
    private ValidationResultRepository results;

    @Autowired
    private TestEntityManager entityManager;

    private UUID documentId;

    @BeforeEach
    void setUp() {
        UUID organizationId = AuthFixtures.organization(entityManager);
        UUID submitterId = AuthFixtures.user(entityManager, organizationId);
        documentId = DocumentFixtures.document(entityManager, organizationId, submitterId);
        entityManager.flush();
    }

    @Test
    void keepsTheRuleTheSeverityAndTheMessageThatTheReviewerWillRead() {
        results.save(new ValidationResult(
                documentId,
                "vat-rate",
                ValidationSeverity.WARNING,
                false,
                "A taxa de IVA calculada (19,4%) não corresponde a 6%, 13% ou 23%."));
        entityManager.flush();
        entityManager.clear();

        assertThat(results.findByDocumentId(documentId)).singleElement().satisfies(result -> {
            assertThat(result.getRuleName()).isEqualTo("vat-rate");
            assertThat(result.getSeverity()).isEqualTo(ValidationSeverity.WARNING);
            assertThat(result.hasPassed()).isFalse();
            assertThat(result.getMessage()).contains("19,4%");
        });
    }

    @Test
    void refusesTwoResultsForTheSameRuleOnTheSameDocument() {
        results.save(new ValidationResult(documentId, "arithmetic", ValidationSeverity.INFO, true, null));
        entityManager.flush();

        results.save(new ValidationResult(documentId, "arithmetic", ValidationSeverity.WARNING, false, "outra vez"));

        assertThatThrownBy(results::flush)
                .as("revalidar reescreve o resultado da regra, não acumula linhas")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearsTheResultsOfADocumentBeforeRevalidating() {
        results.save(new ValidationResult(documentId, "arithmetic", ValidationSeverity.INFO, true, null));
        results.save(new ValidationResult(documentId, "tax-id", ValidationSeverity.INFO, true, null));
        entityManager.flush();

        results.deleteByDocumentId(documentId);
        entityManager.flush();

        assertThat(results.findByDocumentId(documentId)).isEmpty();
    }
}
