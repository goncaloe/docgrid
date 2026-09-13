package com.docgrid.dashboard;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.dashboard.dto.DashboardResponse;

/**
 * Dashboard administrativo: totais, série mensal, categorias e top de fornecedores.
 * Todos os dados são de leitura e filtrados pela organização do utilizador autenticado.
 *
 * <p>Acesso reservado aos papéis {@code FINANCE}, {@code MANAGER} e {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('FINANCE','MANAGER','ADMIN')")
class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserProvider currentUser;

    DashboardController(DashboardService dashboardService, CurrentUserProvider currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /**
     * Dados do dashboard para o período indicado.
     *
     * <p>Quando ambos os parâmetros são omitidos, o período são os últimos 12 meses
     * (incluindo o mês corrente). Se apenas um for fornecido, o outro é calculado a
     * partir dele.
     *
     * @param from início do período ({@code yyyy-MM}); omite para os últimos 12 meses
     * @param to   fim do período ({@code yyyy-MM}); omite para o mês corrente
     * @return resposta com os cinco blocos de agregação
     */
    @GetMapping
    DashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {

        if (from == null && to == null) {
            YearMonth now = YearMonth.now();
            from = now.minusMonths(11);
            to = now;
        } else if (from == null) {
            from = to.minusMonths(11);
        } else if (to == null) {
            to = YearMonth.now();
        }

        return dashboardService.getDashboard(currentUser.currentOrganizationId(), from, to);
    }
}
