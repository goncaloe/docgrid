package com.docgrid.demo;

/**
 * O que cada fatura de demonstração existe para mostrar.
 *
 * <p>Não é decoração: o catálogo planta estes casos em posições fixas para que a fila de
 * revisão tenha sempre os mesmos exemplos — um campo mal lido, uma soma que não bate
 * certo, um NIF inválido, um campo que a extração não leu, e a mesma fatura duas vezes.
 * Sem eles, sessenta documentos limpos não mostravam nada do que o produto faz.
 */
public enum DemoCase {

    /** Tudo bate certo e a confiança é alta: o documento sai de `PROCESSING` já pronto. */
    CLEAN,

    /** Um campo abaixo do limiar de 0,85 — a `MinConfidenceRule` manda-o para revisão. */
    LOW_CONFIDENCE,

    /** `base + IVA` não dá o total: a `ArithmeticRule` apanha-o. */
    BAD_ARITHMETIC,

    /** O dígito de controlo do NIF não fecha: a `TaxIdRule` apanha-o. */
    INVALID_TAX_ID,

    /** A extração não leu um campo obrigatório — nem valor, nem confiança, nem geometria. */
    MISSING_FIELD,

    /** A primeira submissão de uma fatura que vai aparecer outra vez. */
    DUPLICATE_ORIGINAL,

    /** O mesmo NIF e o mesmo número de fatura do {@link #DUPLICATE_ORIGINAL}. */
    DUPLICATE_COPY
}
