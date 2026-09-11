package com.docgrid.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê o {@code Authorization: Bearer <token>} de cada pedido. Um token ausente ou inválido
 * não bloqueia aqui — o pedido segue sem identidade, e é
 * {@code authorizeHttpRequests}/{@code @PreAuthorize} que decide se isso chega para o
 * endpoint pedido.
 *
 * <p>Não é {@code @Component} de propósito: um {@link jakarta.servlet.Filter} registado
 * como bean é também autorregistado pelo Spring Boot como filtro do servlet container,
 * correndo duas vezes. {@link SecurityConfig} constrói-o diretamente e regista-o só na
 * cadeia de segurança.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService
                    .parse(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(claims ->
                            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(claims)));
        }
        chain.doFilter(request, response);
    }
}
