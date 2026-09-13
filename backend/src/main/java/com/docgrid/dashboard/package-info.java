/**
 * Painel de agregações administrativas (dashboard) — totais do período, tendência mensal,
 * categorias, top de fornecedores e taxa de automação.
 *
 * <p>É um modelo de <strong>leitura</strong>: os quatro métodos de {@link
 * com.docgrid.dashboard.DashboardQueries DashboardQueries} fazem consultas SQL diretas
 * sobre {@code documents} e {@code document_events} via {@code JdbcClient}. Não há
 * entidades JPA nem escrita.
 *
 * <p>O isolamento por organização é invariante: todas as queries recebem o
 * {@code organizationId} do {@link com.docgrid.auth.CurrentUserProvider} e filtram por
 * ele.
 */
package com.docgrid.dashboard;
