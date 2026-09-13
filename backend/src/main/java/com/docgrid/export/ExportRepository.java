package com.docgrid.export;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acesso às exportações já fechadas.
 *
 * <p>O isolamento por organização aplica-se em todas as consultas: ler uma exportação de
 * outra organização é como se não existisse.
 */
@Repository
interface ExportRepository extends JpaRepository<Export, UUID> {

    /**
     * Devolve a exportação de um período, se existir. O par é único (índice único na BD).
     */
    Optional<Export> findByOrganizationIdAndPeriodYearAndPeriodMonth(UUID organizationId, int year, int month);

    /**
     * Todas as exportações de um ano, da mais recente à mais antiga.
     */
    List<Export> findByOrganizationIdAndPeriodYearOrderByPeriodMonthDesc(UUID organizationId, int year);

    /**
     * Ler uma exportação pelo id com isolamento por organização — ler uma exportação de
     * outra organização é como se não existisse.
     */
    Optional<Export> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
