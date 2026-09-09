package com.docgrid.storage;

import java.util.Objects;

/**
 * O conteúdo de um objeto, para processamento.
 *
 * <p>O limite do upload (10 MB) faz de {@code byte[]} uma escolha honesta: o conteúdo
 * cabe em memória com folga, e quem o processa precisa dele inteiro para calcular o hash
 * e alimentar a extração, por isso não há valor num stream que só adiaria a leitura.
 */
public record StoredObject(byte[] content) {

    public StoredObject {
        Objects.requireNonNull(content, "content");
    }
}
