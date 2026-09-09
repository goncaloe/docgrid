package com.docgrid.document;

import java.util.Objects;
import java.util.UUID;

/**
 * O autor de uma transição.
 *
 * <p>Ou é uma pessoa, e então identifica-se, ou é o sistema — o worker que consome a fila
 * na etapa 03 — e então não há utilizador nenhum a apontar. A base de dados impõe a mesma
 * regra em {@code ck_document_events_actor}; aqui ela falha mais cedo e com melhor
 * mensagem.
 */
public record Actor(ActorType type, UUID userId) {

    public Actor {
        Objects.requireNonNull(type, "type");
        if (type == ActorType.USER && userId == null) {
            throw new IllegalArgumentException("Um ator do tipo USER tem de identificar o utilizador");
        }
        if (type == ActorType.SYSTEM && userId != null) {
            throw new IllegalArgumentException("Um ator do tipo SYSTEM não tem utilizador");
        }
    }

    public static Actor user(UUID userId) {
        return new Actor(ActorType.USER, userId);
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }
}
