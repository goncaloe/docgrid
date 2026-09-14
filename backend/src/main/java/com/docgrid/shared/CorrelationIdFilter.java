package com.docgrid.shared;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante que cada pedido HTTP tem um id de correlação: aceita o do cliente (saneado,
 * para que texto arbitrário não entre nos logs), ou gera um UUID. O id fica no MDC
 * durante o pedido e volta no header da resposta, para o cliente o poder repetir.
 *
 * <p>A ordem é a mais alta de propósito: corre antes da cadeia do Spring Security (que
 * se regista na ordem −100), para que um 401 — incapacidade de autenticar, logo de
 * diagnóstico difícil — saia já identificado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = Correlation.sanitizeOrGenerate(request.getHeader(Correlation.HEADER));
        Correlation.set(correlationId);
        try {
            response.setHeader(Correlation.HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            Correlation.clear();
        }
    }
}
