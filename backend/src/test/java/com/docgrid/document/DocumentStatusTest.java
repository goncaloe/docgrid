package com.docgrid.document;

import static com.docgrid.document.DocumentStatus.APPROVED;
import static com.docgrid.document.DocumentStatus.EXPORTED;
import static com.docgrid.document.DocumentStatus.EXTRACTED;
import static com.docgrid.document.DocumentStatus.FAILED;
import static com.docgrid.document.DocumentStatus.NEEDS_REVIEW;
import static com.docgrid.document.DocumentStatus.PROCESSING;
import static com.docgrid.document.DocumentStatus.REJECTED;
import static com.docgrid.document.DocumentStatus.UPLOADED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A matriz completa: os sessenta e quatro pares de estados, um a um.
 *
 * <p>As onze transições válidas estão escritas à mão a partir de
 * {@code docs/01-PRODUCT.md}. Derivá-las do próprio enum tornaria o teste uma tautologia:
 * passaria com qualquer tabela de transições, incluindo uma errada.
 */
class DocumentStatusTest {

    private static final Set<Transition> LIFECYCLE = Set.of(
            new Transition(UPLOADED, PROCESSING),
            new Transition(PROCESSING, EXTRACTED),
            new Transition(PROCESSING, NEEDS_REVIEW),
            new Transition(PROCESSING, FAILED),
            new Transition(EXTRACTED, APPROVED),
            new Transition(EXTRACTED, NEEDS_REVIEW),
            new Transition(EXTRACTED, REJECTED),
            new Transition(NEEDS_REVIEW, APPROVED),
            new Transition(NEEDS_REVIEW, REJECTED),
            new Transition(FAILED, PROCESSING),
            new Transition(APPROVED, EXPORTED));

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPairOfStatuses")
    void acceptsExactlyTheTransitionsInTheLifecycle(DocumentStatus from, DocumentStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(LIFECYCLE.contains(new Transition(from, to)));
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void neverAllowsAStatusToTransitionToItself(DocumentStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = DocumentStatus.class,
            names = {"REJECTED", "EXPORTED"})
    void hasNoWayOutOfATerminalStatus(DocumentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(terminal.allowedTransitions()).isEmpty();
    }

    @Test
    void letsAnApprovedDocumentOnlyBeExported() {
        assertThat(APPROVED.allowedTransitions()).containsExactly(EXPORTED);
    }

    @Test
    void letsAFailedDocumentBeProcessedAgain() {
        assertThat(FAILED.allowedTransitions()).containsExactly(PROCESSING);
    }

    static Stream<Arguments> everyPairOfStatuses() {
        return Arrays.stream(DocumentStatus.values())
                .flatMap(from -> Arrays.stream(DocumentStatus.values()).map(to -> Arguments.of(from, to)));
    }

    private record Transition(DocumentStatus from, DocumentStatus to) {}
}
