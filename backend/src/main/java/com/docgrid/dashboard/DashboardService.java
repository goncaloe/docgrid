package com.docgrid.dashboard;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.dashboard.dto.DashboardResponse;
import com.docgrid.dashboard.dto.DashboardResponse.CategoryTotal;
import com.docgrid.dashboard.dto.DashboardResponse.MonthlyTotal;
import com.docgrid.dashboard.dto.DashboardResponse.Operations;
import com.docgrid.dashboard.dto.DashboardResponse.PeriodTotals;
import com.docgrid.dashboard.dto.DashboardResponse.SupplierTotal;

/**
 * Monta a resposta completa do dashboard a partir das cinco queries de agregação.
 *
 * <p>Todas as queries respeitam o isolamento por organização através do
 * {@link CurrentUserProvider}, injetado também aqui para uso do controller.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardQueries queries;

    DashboardService(DashboardQueries queries) {
        this.queries = queries;
    }

    /**
     * Produz o {@link DashboardResponse} para a organização e intervalo indicados.
     *
     * @param organizationId id da organização (vem do {@link CurrentUserProvider})
     * @param from           início do intervalo (inclusive)
     * @param to             fim do intervalo (inclusive)
     * @return resposta completa com os cinco blocos de agregação
     */
    public DashboardResponse getDashboard(UUID organizationId, YearMonth from, YearMonth to) {
        PeriodTotals totals = queries.periodTotals(organizationId, from, to);
        Operations operations = queries.operations(organizationId, from, to);
        List<MonthlyTotal> monthly = queries.monthlyTotals(organizationId, from, to);
        List<CategoryTotal> categories = queries.categoryTotals(organizationId, from, to);
        List<SupplierTotal> topSuppliers = queries.topSuppliers(organizationId, from, to, 10);

        return new DashboardResponse(totals, operations, monthly, categories, topSuppliers);
    }
}
