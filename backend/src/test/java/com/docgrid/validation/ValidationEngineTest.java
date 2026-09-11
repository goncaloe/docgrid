package com.docgrid.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.docgrid.auth.AuthFixtures;
import com.docgrid.document.DocumentFixtures;
import com.docgrid.support.RepositoryTest;

/**
 * O motor com regras de teste deterministas — não as 8 regras de negócio reais, que já têm
 * cobertura própria. Aqui prova-se só a orquestração: gravar, revalidar, e resumir.
 */
@RepositoryTest
class ValidationEngineTest {

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
    void writesOneRowPerRuleThatHasAnOpinion() {
        ValidationEngine engine = new ValidationEngine(
                List.of(
                        alwaysAbstains("SILENT"),
                        passing("ALWAYS_PASSES"),
                        failing("ALWAYS_FAILS", ValidationSeverity.WARNING, "algo não bate certo")),
                results);

        engine.validate(context());
        entityManager.flush();
        entityManager.clear();

        assertThat(results.findByDocumentId(documentId))
                .extracting(ValidationResult::getRuleName)
                .containsExactlyInAnyOrder("ALWAYS_PASSES", "ALWAYS_FAILS");
    }

    @Test
    void requiresReviewWhenAnyNonInfoRuleFails() {
        ValidationEngine engine = new ValidationEngine(
                List.of(passing("ARITHMETIC"), failing("VAT_RATE", ValidationSeverity.WARNING, "taxa estranha")),
                results);

        ValidationSummary summary = engine.validate(context());

        assertThat(summary.requiresReview()).isTrue();
        assertThat(summary.reason()).isEqualTo("taxa estranha");
    }

    @Test
    void doesNotRequireReviewWhenOnlyInfoRulesSpeak() {
        ValidationEngine engine = new ValidationEngine(
                List.of(failing("APPROVAL_THRESHOLD", ValidationSeverity.INFO, "informativo")), results);

        ValidationSummary summary = engine.validate(context());

        assertThat(summary.requiresReview()).isFalse();
        assertThat(summary.reason()).isNull();
    }

    @Test
    void combinesTheMessagesOfEveryFailingRule() {
        ValidationEngine engine = new ValidationEngine(
                List.of(
                        failing("ARITHMETIC", ValidationSeverity.WARNING, "aritmética errada"),
                        failing("TAX_ID", ValidationSeverity.WARNING, "NIF inválido")),
                results);

        ValidationSummary summary = engine.validate(context());

        assertThat(summary.reason()).isEqualTo("aritmética errada; NIF inválido");
    }

    @Test
    void revalidatingReplacesThePreviousResultsInsteadOfAccumulating() {
        ValidationEngine first =
                new ValidationEngine(List.of(failing("ARITHMETIC", ValidationSeverity.WARNING, "v1")), results);
        first.validate(context());
        entityManager.flush();
        entityManager.clear();

        ValidationEngine second = new ValidationEngine(List.of(passing("ARITHMETIC")), results);
        second.validate(context());
        entityManager.flush();
        entityManager.clear();

        assertThat(results.findByDocumentId(documentId)).singleElement().satisfies(result -> {
            assertThat(result.getRuleName()).isEqualTo("ARITHMETIC");
            assertThat(result.hasPassed()).isTrue();
        });
    }

    private ValidationContext context() {
        return new ValidationContext(
                documentId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                null,
                null,
                null,
                null,
                0,
                null);
    }

    private static ValidationRule passing(String name) {
        return new FixedRule(name, Optional.of(new RuleOutcome(ValidationSeverity.WARNING, true, null)));
    }

    private static ValidationRule failing(String name, ValidationSeverity severity, String message) {
        return new FixedRule(name, Optional.of(new RuleOutcome(severity, false, message)));
    }

    private static ValidationRule alwaysAbstains(String name) {
        return new FixedRule(name, Optional.empty());
    }

    private record FixedRule(String ruleName, Optional<RuleOutcome> outcome) implements ValidationRule {
        @Override
        public Optional<RuleOutcome> evaluate(ValidationContext context) {
            return outcome;
        }
    }
}
