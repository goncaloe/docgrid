package com.docgrid.validation;

import java.util.List;
import java.util.UUID;

/**
 * Os resultados de validação correntes de um documento, para o detalhe da etapa 06 poder
 * mostrar porque é que um documento precisa de revisão.
 */
public interface ValidationResultProvider {

    List<ValidationResultView> resultsFor(UUID documentId);
}
