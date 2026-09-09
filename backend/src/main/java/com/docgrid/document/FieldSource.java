package com.docgrid.document;

/** De onde veio o valor de um campo. */
public enum FieldSource {
    /** Lido pelo motor de extração, e por isso com grau de confiança. */
    AI,
    /** Escrito por uma pessoa, que não tem grau de confiança a declarar. */
    HUMAN
}
